package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.financeservice.controller.FinanceReadController;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PayrollRunBatchQueryTest {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.projection-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.projection-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.projection-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "finance-batch-test-secret-012345678901234567890");
    }

    @Autowired
    private FinanceReadController financeReadController;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private NamedParameterJdbcTemplate masterJdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    finance.payroll_result_allocation,
                    finance.payroll_result_line,
                    finance.payroll_attendance_snapshot,
                    finance.payroll_contract_snapshot,
                    finance.payroll_employee_result,
                    finance.expense_payroll,
                    finance.expense_record,
                    finance.outbox_event,
                    finance.payroll_run,
                    finance.payroll_period
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldAssemblePayrollRunsWithEmployeesInBatch() {
        // Seed Period
        long periodId = jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_period (
                    region_id, reference_code, name, start_date, end_date, pay_date, status, created_at, updated_at
                ) VALUES (
                    1, 'PP-BATCH-001', 'Batch Test Period', DATE '2026-03-01', DATE '2026-03-31', DATE '2026-04-05', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource(), Long.class);

        // Seed Run 1 and Employees
        long run1Id = insertRun(periodId, "RUN-B1");
        long res1 = insertEmployeeResult(run1Id, 501L);
        insertAllocation(res1, 301L);
        insertLine(res1, "BASE");

        long res2 = insertEmployeeResult(run1Id, 502L);
        insertAllocation(res2, 301L);

        // Seed Run 2 and Employees
        long run2Id = insertRun(periodId, "RUN-B2");
        long res3 = insertEmployeeResult(run2Id, 503L);
        insertAllocation(res3, 302L);

        // Seed Run 3 with no employees
        long run3Id = insertRun(periodId, "RUN-B3");

        FernPrincipal principal = new FernPrincipal(
                1L,
                "finance-tester",
                Set.of("finance"),
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ, PermissionCodes.FINANCE_PAYROLL_DETAIL_READ),
                new ScopeRoots(true, List.of(), List.of()),
                1L, 1L, "jti"
        );

        // Execute batch query
        List<PayrollRunResponse> runs = financeReadController.listPayrollRuns(principal, 1L, null);

        // Assert grouping and mapping
        assertThat(runs).hasSize(3);

        // Due to descending order by id, runs will be B3, B2, B1
        PayrollRunResponse runB3 = runs.get(0);
        assertThat(runB3.id()).isEqualTo(run3Id);
        assertThat(runB3.employees()).isEmpty();

        PayrollRunResponse runB2 = runs.get(1);
        assertThat(runB2.id()).isEqualTo(run2Id);
        assertThat(runB2.employees()).hasSize(1);
        assertThat(runB2.employees().getFirst().employeeId()).isEqualTo(503L);
        assertThat(runB2.employees().getFirst().allocations()).hasSize(1);
        assertThat(runB2.employees().getFirst().allocations().getFirst().outletId()).isEqualTo(302L);

        PayrollRunResponse runB1 = runs.get(2);
        assertThat(runB1.id()).isEqualTo(run1Id);
        assertThat(runB1.employees()).hasSize(2);
        assertThat(runB1.employees()).extracting("employeeId").containsExactlyInAnyOrder(501L, 502L);
        
        // Assert lines mapped properly
        var emp1 = runB1.employees().stream().filter(e -> e.employeeId().equals(501L)).findFirst().orElseThrow();
        assertThat(emp1.lines()).hasSize(1);
        assertThat(emp1.lines().getFirst().lineType()).isEqualTo("BASE");
    }

    private long insertRun(long periodId, String code) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_run (
                    payroll_period_id, run_code, run_date, status, total_amount, created_at, updated_at
                ) VALUES (
                    :periodId, :code, DATE '2026-04-01', 'DRAFT', 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource("periodId", periodId).addValue("code", code), Long.class);
    }

    private long insertEmployeeResult(long runId, long employeeId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_employee_result (
                    payroll_run_id, employee_id, contract_id, outlet_id, gross_pay, deduction_amount, tax_amount, net_pay,
                    payment_status, work_days, work_hours, overtime_hours, created_at, updated_at
                ) VALUES (
                    :runId, :employeeId, 701, 301, 120.00, 10.00, 10.00, 100.00,
                    'UNPAID', 20, 160, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource("runId", runId).addValue("employeeId", employeeId), Long.class);
    }

    private void insertAllocation(long resultId, long outletId) {
        jdbcTemplate.update("""
                INSERT INTO finance.payroll_result_allocation (
                    payroll_employee_result_id, outlet_id, work_hours, allocated_amount, created_at
                ) VALUES (
                    :resultId, :outletId, 160, 100.00, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource("resultId", resultId).addValue("outletId", outletId));
    }

    private void insertLine(long resultId, String type) {
        jdbcTemplate.update("""
                INSERT INTO finance.payroll_result_line (
                    payroll_employee_result_id, line_type, description, amount, created_at
                ) VALUES (
                    :resultId, :type, 'Line item', 100.00, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource("resultId", resultId).addValue("type", type));
    }
}
