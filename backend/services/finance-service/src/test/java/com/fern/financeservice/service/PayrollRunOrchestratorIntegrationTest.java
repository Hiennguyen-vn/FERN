package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollRunRequest;
import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class PayrollRunOrchestratorIntegrationTest {
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
        registry.add("fern.security.jwt.secret", () -> "finance-payroll-run-orchestrator-secret-012345678901234567890");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private FinancePayrollService financePayrollService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private NamedParameterJdbcTemplate masterJdbcTemplate;

    @MockBean
    private PayrollHrClient payrollHrClient;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

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
        masterJdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    config.document_numbering_rule,
                    config.system_policy
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldAvoidPartialWritesWhenHrFetchFailsBeforePayrollRunPersist() {
        var principal = payrollPrincipal();
        Long payrollPeriodId = financePayrollService.createPayrollPeriod(
                principal,
                new CreatePayrollPeriodRequest(
                        1L,
                        "March 2026",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 5),
                        null
                )
        ).id();
        when(payrollHrClient.fetchEffectiveContracts(anyLong(), any(), any(), anyString(), any()))
                .thenThrow(new BadRequestException("Unable to fetch effective contracts from HR: timeout"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> financePayrollService.createPayrollRun(
                        principal,
                        new CreatePayrollRunRequest(payrollPeriodId, LocalDate.of(2026, 4, 1), null),
                        "corr-payroll-timeout"
                ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unable to fetch effective contracts from HR");

        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM finance.payroll_run", Integer.class)).isZero();
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM finance.payroll_contract_snapshot", Integer.class)).isZero();
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM finance.payroll_employee_result", Integer.class)).isZero();
    }

    @Test
    void shouldProduceZeroAmountRunWhenApprovedAttendanceIsEmpty() {
        FernPrincipal principal = payrollPrincipal();
        when(payrollHrClient.fetchEffectiveContracts(anyLong(), any(), any(), anyString(), any()))
                .thenReturn(List.of(new EffectiveContract(
                        701L,
                        501L,
                        1L,
                        "FULL_TIME",
                        "MONTHLY",
                        new BigDecimal("3000.00"),
                        "TAX-001",
                        LocalDate.of(2026, 1, 1),
                        null
                )));
        when(payrollHrClient.fetchApprovedAttendance(anyLong(), any(), any(), anyString(), any()))
                .thenReturn(List.of());

        Long payrollPeriodId = financePayrollService.createPayrollPeriod(
                principal,
                new CreatePayrollPeriodRequest(
                        1L,
                        "March 2026",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 5),
                        null
                ),
                "corr-payroll-period-empty-attendance"
        ).id();

        var run = financePayrollService.createPayrollRun(
                principal,
                new CreatePayrollRunRequest(payrollPeriodId, LocalDate.of(2026, 4, 1), null),
                "corr-payroll-run-empty-attendance"
        );

        assertThat(run.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(run.employees()).isEmpty();
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM finance.payroll_employee_result", Integer.class)).isZero();
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM finance.payroll_attendance_snapshot", Integer.class)).isZero();
    }

    @Test
    void shouldEmitDeterministicPayrollCalculatedEnvelopeOnApproval() {
        FernPrincipal principal = payrollWorkflowPrincipal();
        when(payrollHrClient.fetchEffectiveContracts(anyLong(), any(), any(), anyString(), any()))
                .thenReturn(List.of(new EffectiveContract(
                        701L,
                        501L,
                        1L,
                        "FULL_TIME",
                        "MONTHLY",
                        new BigDecimal("3000.00"),
                        "TAX-001",
                        LocalDate.of(2026, 1, 1),
                        null
                )));
        when(payrollHrClient.fetchApprovedAttendance(anyLong(), any(), any(), anyString(), any()))
                .thenReturn(List.of(new ApprovedAttendance(
                        801L,
                        901L,
                        501L,
                        1L,
                        301L,
                        701L,
                        LocalDate.of(2026, 3, 15),
                        "PRESENT",
                        new BigDecimal("8.00"),
                        BigDecimal.ZERO
                )));

        Long payrollPeriodId = financePayrollService.createPayrollPeriod(
                principal,
                new CreatePayrollPeriodRequest(
                        1L,
                        "March 2026",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 5),
                        null
                ),
                "corr-payroll-period"
        ).id();
        Long runId = financePayrollService.createPayrollRun(
                principal,
                new CreatePayrollRunRequest(payrollPeriodId, LocalDate.of(2026, 4, 1), null),
                "corr-payroll-run-create"
        ).id();

        financePayrollService.submitPayrollRun(principal, runId, "submit payroll", "corr-payroll-run-submit");
        financePayrollService.approvePayrollRun(principal, runId, "approve payroll", "corr-payroll-run-approve");

        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'PAYROLL_RUN'
                  AND aggregate_id = '%d'
                  AND event_type = 'payroll.calculated'
                """.formatted(runId), Integer.class)).isEqualTo(1);
        assertThat(outboxPayloadField("PAYROLL_RUN", runId.toString(), "payroll.calculated", "eventId"))
                .matches("\\d+");
        assertThat(outboxPayloadField("PAYROLL_RUN", runId.toString(), "payroll.calculated", "idempotencyKey"))
                .isEqualTo("payroll.calculated:run:" + runId);
    }

    @Test
    void shouldEmitDeterministicPayrollPostedAndExpensePostedEnvelopeOnMarkPaid() {
        masterJdbcTemplate.update("""
                INSERT INTO config.document_numbering_rule (
                    document_type, prefix, next_number, reset_period, is_active, updated_at
                ) VALUES (
                    'PAYROLL_EXPENSE', 'EXP', 1, 'NEVER', TRUE, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource());

        long periodId = jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_period (
                    region_id, reference_code, name, start_date, end_date, pay_date, status, created_at, updated_at
                ) VALUES (
                    1, 'PP-000001', 'March payroll', DATE '2026-03-01', DATE '2026-03-31', DATE '2026-04-05', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource(), Long.class);
        long runId = jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_run (
                    payroll_period_id, run_code, run_date, status, total_amount, created_at, updated_at
                ) VALUES (
                    :periodId, 'RUN-000001', DATE '2026-04-01', 'APPROVED', 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource("periodId", periodId), Long.class);
        long resultId = jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_employee_result (
                    payroll_run_id, employee_id, contract_id, outlet_id, gross_pay, deduction_amount, tax_amount, net_pay,
                    payment_status, work_days, work_hours, overtime_hours, created_at, updated_at
                ) VALUES (
                    :runId, 501, 701, 301, 120.00, 10.00, 10.00, 100.00,
                    'UNPAID', 20, 160, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource("runId", runId), Long.class);
        jdbcTemplate.update("""
                INSERT INTO finance.payroll_result_allocation (
                    payroll_employee_result_id, outlet_id, work_hours, allocated_amount, created_at
                ) VALUES (
                    :resultId, 301, 160, 100.00, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource("resultId", resultId));

        financePayrollService.markPayrollPaid(
                payrollPayPrincipal(),
                runId,
                new MarkPaidRequest("PAY-2026-0001", "marked paid"),
                "corr-payroll-paid"
        );

        Long expenseRecordId = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT id
                FROM finance.expense_record
                WHERE source_type = 'PAYROLL'
                ORDER BY id
                LIMIT 1
                """, Long.class);

        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'PAYROLL_RUN'
                  AND aggregate_id = '%d'
                  AND event_type = 'payroll.posted'
                """.formatted(runId), Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '%d'
                  AND event_type = 'finance.expense.posted'
                """.formatted(expenseRecordId), Integer.class)).isEqualTo(1);
        assertThat(outboxPayloadField("PAYROLL_RUN", Long.toString(runId), "payroll.posted", "eventId"))
                .matches("\\d+");
        assertThat(outboxPayloadField("PAYROLL_RUN", Long.toString(runId), "payroll.posted", "idempotencyKey"))
                .isEqualTo("payroll.posted:run:" + runId);
        assertThat(outboxPayloadField("EXPENSE_RECORD", expenseRecordId.toString(), "finance.expense.posted", "eventId"))
                .matches("\\d+");
        assertThat(outboxPayloadField("EXPENSE_RECORD", expenseRecordId.toString(), "finance.expense.posted", "idempotencyKey"))
                .isEqualTo("finance.expense.posted:expense:" + expenseRecordId);
    }

    private FernPrincipal payrollPrincipal() {
        return new FernPrincipal(
                1L,
                "finance-payroll-tester",
                Set.of("finance"),
                Set.of(PermissionCodes.FINANCE_PAYROLL_PREPARE, PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "finance-payroll-tester-jti"
        );
    }

    private FernPrincipal payrollWorkflowPrincipal() {
        return new FernPrincipal(
                1L,
                "finance-payroll-workflow-tester",
                Set.of("finance"),
                Set.of(
                        PermissionCodes.FINANCE_PAYROLL_PREPARE,
                        PermissionCodes.FINANCE_PAYROLL_READ,
                        PermissionCodes.FINANCE_PAYROLL_APPROVE,
                        PermissionCodes.FINANCE_PAYROLL_PAY
                ),
                new ScopeRoots(false, List.of(1L), List.of()),
                1L,
                1L,
                "finance-payroll-workflow-tester-jti"
        );
    }

    private FernPrincipal payrollPayPrincipal() {
        return new FernPrincipal(
                1L,
                "finance-payroll-pay-tester",
                Set.of("finance"),
                Set.of(PermissionCodes.FINANCE_PAYROLL_PAY, PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of()),
                1L,
                1L,
                "finance-payroll-pay-tester-jti"
        );
    }

    private String outboxPayloadField(String aggregateType, String aggregateId, String eventType, String field) {
        return jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT payload ->> '%s'
                FROM finance.outbox_event
                WHERE aggregate_type = '%s'
                  AND aggregate_id = '%s'
                  AND event_type = '%s'
                ORDER BY created_at, id
                LIMIT 1
                """.formatted(field, aggregateType, aggregateId, eventType), String.class);
    }
}
