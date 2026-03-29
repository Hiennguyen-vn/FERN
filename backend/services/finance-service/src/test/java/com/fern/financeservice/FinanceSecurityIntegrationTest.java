package com.fern.financeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.controller.FinanceReadController;
import com.fern.financeservice.service.FinancePayrollService;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FinanceSecurityIntegrationTest {
    private static final String TEST_SECRET = "finance-test-secret-key-012345678901234567890123456";

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
        registry.add("fern.security.jwt.secret", () -> TEST_SECRET);
    }

    @Autowired
    private FinancePayrollService financePayrollService;

    @Autowired
    private FinanceReadController financeReadController;

    @Autowired
    private FernJwtService jwtService;

    @Autowired
    private FernServiceTokenSupport serviceTokenSupport;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private NamedParameterJdbcTemplate masterJdbcTemplate;

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
                    finance.expense_inventory_purchase,
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
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void shouldRejectRegionalUserFromGlobalPayrollLists() {
        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        assertThatThrownBy(() -> financePayrollService.listPayrollPeriods(principal, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> financePayrollService.listPayrollRuns(principal, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldRejectInvalidPayrollPeriodDatesOverHttp() throws Exception {
        String token = userToken(
                Set.of(PermissionCodes.FINANCE_PAYROLL_PREPARE),
                new ScopeRoots(true, List.of(), List.of())
        );

        mockMvc.perform(post("/payroll-periods")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "name": "Invalid Period",
                                  "startDate": "2026-03-31",
                                  "endDate": "2026-03-01",
                                  "payDate": "2026-02-28"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.dateRangeValid").value("endDate must be on or after startDate"));
    }

    @Test
    void shouldRestrictFinanceConfigToSystemScope() {
        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_CONFIG_READ, PermissionCodes.FINANCE_CONFIG_WRITE),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        assertThatThrownBy(() -> financePayrollService.getNumberingRule(principal, "PAYROLL_RUN"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> financePayrollService.getSystemPolicy(principal, "payroll.tax"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldIssueUniqueDocumentNumbersUnderConcurrency() throws Exception {
        masterJdbcTemplate.update("""
                INSERT INTO config.document_numbering_rule (
                    document_type, prefix, next_number, reset_period, is_active, updated_at
                ) VALUES (
                    'PAYROLL_PERIOD', 'PP', 1, 'NEVER', TRUE, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource());

        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_PREPARE, PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(true, List.of(), List.of())
        );
        int taskCount = 8;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                final int taskIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    financePayrollService.createPayrollPeriod(
                            principal,
                            new com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest(
                                    100L + taskIndex,
                                    "Period " + taskIndex,
                                    LocalDate.of(2026, 3, 1),
                                    LocalDate.of(2026, 3, 31),
                                    LocalDate.of(2026, 4, 5),
                                    null
                            )
                    );
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        List<String> codes = jdbcTemplate.getJdbcTemplate().queryForList("""
                SELECT reference_code
                FROM finance.payroll_period
                ORDER BY reference_code
                """, String.class);
        assertThat(codes).containsExactly(
                "PP-000001",
                "PP-000002",
                "PP-000003",
                "PP-000004",
                "PP-000005",
                "PP-000006",
                "PP-000007",
                "PP-000008"
        );
        Long nextNumber = masterJdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT next_number
                FROM config.document_numbering_rule
                WHERE document_type = 'PAYROLL_PERIOD'
                """, Long.class);
        assertThat(nextNumber).isEqualTo(9L);
    }

    @Test
    void shouldMarkPayrollPaidOnlyOnceUnderConcurrency() throws Exception {
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

        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_PAY, PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return invokeMarkPaid(principal, runId);
            });
            Future<Object> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return invokeMarkPaid(principal, runId);
            });
            ready.await();
            start.countDown();

            List<Object> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes.stream().filter(outcome -> outcome instanceof String && outcome.equals("OK")).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(outcome -> outcome instanceof Exception).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Integer expenseCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM finance.expense_record WHERE source_type = 'PAYROLL'
                """, Integer.class);
        Integer outboxCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM finance.outbox_event WHERE event_type = 'payroll.posted'
                """, Integer.class);
        String status = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT status FROM finance.payroll_run WHERE id = %d
                """.formatted(runId), String.class);

        assertThat(expenseCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
        assertThat(status).isEqualTo("PAID");
    }

    @Test
    void shouldCancelDraftPayrollRun() {
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
                    payroll_period_id, run_code, run_date, status, note, total_amount, created_at, updated_at
                ) VALUES (
                    :periodId, 'RUN-000001', DATE '2026-04-01', 'DRAFT', 'initial', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource("periodId", periodId), Long.class);

        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_PREPARE, PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        var response = financePayrollService.cancelPayrollRun(principal, runId, "cancelled by reviewer", "corr-cancel-payroll");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.note()).isEqualTo("cancelled by reviewer");
        String persistedStatus = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT status
                FROM finance.payroll_run
                WHERE id = %d
                """.formatted(runId), String.class);
        assertThat(persistedStatus).isEqualTo("CANCELLED");
    }

    @Test
    void shouldRejectCancellingApprovedPayrollRun() {
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
                    payroll_period_id, run_code, run_date, status, note, total_amount, created_at, updated_at
                ) VALUES (
                    :periodId, 'RUN-000001', DATE '2026-04-01', 'APPROVED', 'approved', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, new MapSqlParameterSource("periodId", periodId), Long.class);

        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_PREPARE, PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        assertThatThrownBy(() -> financePayrollService.cancelPayrollRun(principal, runId, "cancelled by reviewer", "corr-cancel-payroll"))
                .isInstanceOf(com.fern.platform.common.BadRequestException.class)
                .hasMessageContaining("Only draft or rejected payroll runs can be cancelled");
    }

    @Test
    void shouldRejectStaleServiceToken() throws Exception {
        redisTemplate.opsForValue().set("fern:versions:policy", "5");
        redisTemplate.opsForValue().set("fern:versions:scope", "7");

        mockMvc.perform(get("/payroll-periods")
                        .header("Authorization", "Bearer " + serviceTokenWithVersions(0L, 0L, Set.of(PermissionCodes.FINANCE_PAYROLL_READ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBlacklistedServiceToken() throws Exception {
        redisTemplate.opsForValue().set("fern:versions:policy", "5");
        redisTemplate.opsForValue().set("fern:versions:scope", "7");
        String token = serviceTokenWithVersions(7L, 7L, Set.of(PermissionCodes.FINANCE_PAYROLL_READ));
        FernJwtClaims claims = jwtService.decode(token);
        redisTemplate.opsForValue().set("fern:iam:blacklist:" + claims.jti(), "1");

        mockMvc.perform(get("/payroll-periods").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptCurrentVersionServiceToken() throws Exception {
        redisTemplate.opsForValue().set("fern:versions:policy", "9");
        redisTemplate.opsForValue().set("fern:versions:scope", "11");

        mockMvc.perform(get("/payroll-periods")
                        .header("Authorization", "Bearer " + serviceTokenSupport.issueToken("finance-service", Set.of(PermissionCodes.FINANCE_PAYROLL_READ))))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("security-gap")
    void shouldRejectUserTokenWithWrongIssuerOverHttp() throws Exception {
        long periodId = seedPayrollPeriod(1L, "PP-WRONG-ISSUER-001");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        mockMvc.perform(get("/payroll-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + userTokenWithIdentity(
                                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                                new ScopeRoots(false, List.of(1L), List.of()),
                                "rogue-issuer",
                                Set.of("finance-service")
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("security-gap")
    void shouldRejectUserTokenWithWrongAudienceOverHttp() throws Exception {
        long periodId = seedPayrollPeriod(1L, "PP-WRONG-AUD-001");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        mockMvc.perform(get("/payroll-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + userTokenWithIdentity(
                                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                                new ScopeRoots(false, List.of(1L), List.of()),
                                "iam-service",
                                Set.of("hr-service")
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("security-gap")
    void shouldIssueFinanceServiceTokenForHrAudience() {
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        FernJwtClaims claims = jwtService.decode(serviceTokenSupport.issueToken(
                "finance-service",
                "hr-service",
                Set.of(PermissionCodes.HR_INTERNAL_READ)
        ));

        assertThat(claims.principalType()).isEqualTo(FernPrincipalType.SERVICE);
        assertThat(claims.issuer()).isEqualTo("finance-service");
        assertThat(claims.audience()).containsExactly("hr-service");
    }

    @Test
    void shouldRejectBlacklistedUserTokenOverHttp() throws Exception {
        long periodId = seedPayrollPeriod(1L, "PP-BLACKLIST-001");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");
        String token = userToken(
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );
        FernJwtClaims claims = jwtService.decode(token);
        redisTemplate.opsForValue().set("fern:iam:blacklist:" + claims.jti(), "1");

        mockMvc.perform(get("/payroll-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectStaleUserScopeTokenOverHttp() throws Exception {
        long periodId = seedPayrollPeriod(1L, "PP-STALE-USER-001");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "2");
        String staleToken = userTokenWithVersions(
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of()),
                1L,
                1L
        );

        mockMvc.perform(get("/payroll-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectOutletScopedUserFromRegionPayrollApiOverHttp() throws Exception {
        long periodId = seedPayrollPeriod(1L, "PP-HTTP-001");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        mockMvc.perform(get("/payroll-periods/{id}", periodId)
                        .header("Authorization", "Bearer " + userToken(
                                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                                new ScopeRoots(false, List.of(), List.of(301L))
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/payroll-periods")
                        .param("regionId", "1")
                        .header("Authorization", "Bearer " + userToken(
                                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                                new ScopeRoots(false, List.of(), List.of(301L))
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectReadingPayrollObjectOutsideRegionalScopeOverHttp() throws Exception {
        long regionOnePeriodId = seedPayrollPeriod(1L, "PP-REGION-001");
        long regionTwoPeriodId = seedPayrollPeriod(2L, "PP-REGION-002");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");
        String regionOneToken = userToken(
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        mockMvc.perform(get("/payroll-periods/{id}", regionOnePeriodId)
                        .header("Authorization", "Bearer " + regionOneToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payroll-periods/{id}", regionTwoPeriodId)
                        .header("Authorization", "Bearer " + regionOneToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldHidePayrollEmployeesWithoutDetailPermission() {
        long runId = seedPayrollRunWithEmployeeResult();
        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        var response = financeReadController.getPayrollRun(principal, runId);
        var listed = financeReadController.listPayrollRuns(principal, 1L);

        assertThat(response.employees()).isEmpty();
        assertThat(listed).singleElement().satisfies(run -> assertThat(run.employees()).isEmpty());
    }

    @Test
    void shouldExposePayrollEmployeesWithDetailPermission() {
        long runId = seedPayrollRunWithEmployeeResult();
        FernPrincipal principal = principal(
                Set.of(PermissionCodes.FINANCE_PAYROLL_READ, PermissionCodes.FINANCE_PAYROLL_DETAIL_READ),
                new ScopeRoots(false, List.of(1L), List.of())
        );

        var response = financeReadController.getPayrollRun(principal, runId);
        var listed = financeReadController.listPayrollRuns(principal, 1L);

        assertThat(response.employees()).hasSize(1);
        assertThat(response.employees().getFirst().allocations()).hasSize(1);
        assertThat(listed).singleElement().satisfies(run -> assertThat(run.employees()).hasSize(1));
    }

    private Object invokeMarkPaid(FernPrincipal principal, long runId) {
        try {
            financePayrollService.markPayrollPaid(principal, runId, new MarkPaidRequest("PAY-REF-1", "paid"));
            return "OK";
        } catch (Exception exception) {
            return exception;
        }
    }

    private String serviceTokenWithVersions(long policyVersion, long scopeVersion, Set<String> permissions) {
        Instant now = Instant.now();
        return jwtService.encode(
                new FernJwtClaims(
                        null,
                        "finance-service",
                        Set.of(),
                        permissions,
                        new ScopeRoots(true, List.of(), List.of()),
                        policyVersion,
                        scopeVersion,
                        UUID.randomUUID().toString(),
                        now,
                        now.plus(jwtService.serviceTokenTtl()),
                        com.fern.platform.common.FernPrincipalType.SERVICE
                ),
                jwtService.serviceTokenTtl()
        );
    }

    private String userToken(Set<String> permissions, ScopeRoots scopeRoots) {
        return userTokenWithVersions(permissions, scopeRoots, 1L, 1L);
    }

    private String userTokenWithVersions(Set<String> permissions, ScopeRoots scopeRoots, long policyVersion, long scopeVersion) {
        Instant now = Instant.now();
        return jwtService.encode(
                new FernJwtClaims(
                        100L,
                        "finance-http-user",
                        Set.of("finance"),
                        permissions,
                        scopeRoots,
                        policyVersion,
                        scopeVersion,
                        UUID.randomUUID().toString(),
                        now,
                        now.plus(jwtService.accessTokenTtl()),
                        com.fern.platform.common.FernPrincipalType.USER
                ),
                jwtService.accessTokenTtl()
        );
    }

    private String userTokenWithIdentity(Set<String> permissions, ScopeRoots scopeRoots, String issuer, Set<String> audience) {
        Instant now = Instant.now();
        return jwtService.encode(
                new FernJwtClaims(
                        100L,
                        "finance-http-user",
                        Set.of("finance"),
                        permissions,
                        scopeRoots,
                        1L,
                        1L,
                        UUID.randomUUID().toString(),
                        now,
                        now.plus(jwtService.accessTokenTtl()),
                        FernPrincipalType.USER,
                        issuer,
                        audience
                ),
                jwtService.accessTokenTtl()
        );
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots) {
        return new FernPrincipal(
                100L,
                "finance-user",
                Set.of("finance"),
                permissions,
                scopeRoots,
                1L,
                1L,
                UUID.randomUUID().toString()
        );
    }

    private long seedPayrollPeriod(Long regionId, String referenceCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO finance.payroll_period (
                    region_id, reference_code, name, start_date, end_date, pay_date, status, created_at, updated_at
                ) VALUES (
                    :regionId, :referenceCode, :name, DATE '2026-03-01', DATE '2026-03-31', DATE '2026-04-05', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("regionId", regionId)
                        .addValue("referenceCode", referenceCode)
                        .addValue("name", "Period " + referenceCode),
                Long.class
        );
    }

    private long seedPayrollRunWithEmployeeResult() {
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
                    payroll_period_id, run_code, run_date, status, total_amount, payment_ref, note, submitted_at, approved_at, paid_at, created_at, updated_at
                ) VALUES (
                    :periodId, 'RUN-000001', DATE '2026-04-01', 'APPROVED', 100.00, 'PAY-001', 'ready', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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
                INSERT INTO finance.payroll_result_line (
                    payroll_employee_result_id, line_type, description, amount, created_at
                ) VALUES (
                    :resultId, 'BASE', 'Base salary', 120.00, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource("resultId", resultId));
        jdbcTemplate.update("""
                INSERT INTO finance.payroll_result_allocation (
                    payroll_employee_result_id, outlet_id, work_hours, allocated_amount, created_at
                ) VALUES (
                    :resultId, 301, 160, 100.00, CURRENT_TIMESTAMP
                )
                """, new MapSqlParameterSource("resultId", resultId));
        return runId;
    }
}
