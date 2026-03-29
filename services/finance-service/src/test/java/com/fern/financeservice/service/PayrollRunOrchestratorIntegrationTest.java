package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollRunRequest;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
}
