package com.fern.reportservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.reportservice.service.ReportService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReportServiceIntegrationTest {
    private static final Path EXPORT_DIR = createExportDir();
    private static final AtomicLong TOKEN_SEQUENCE = new AtomicLong();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("report"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.report.export.base-dir", () -> EXPORT_DIR.toString());
        registry.add("fern.report.export.preview-row-limit", () -> "5");
        registry.add("fern.report.export.worker-delay-ms", () -> "60000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportService reportService;

    @BeforeEach
    void setUp() throws IOException {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    report.outbox_event,
                    report.company_daily_outlet,
                    report.region_daily_event,
                    report.export_job,
                    report.company_daily_summary,
                    report.region_daily_summary,
                    report.expense_fact,
                    report.payroll_fact,
                    report.attendance_fact,
                    report.procurement_fact,
                    report.inventory_movement_fact,
                    report.payment_fact,
                    report.sales_fact,
                    raw_events.event_landing
                RESTART IDENTITY CASCADE
                """);
        try (var stream = Files.list(EXPORT_DIR)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to clean export artifact " + path, exception);
                }
            });
        }
    }

    @Test
    void shouldIngestExpenseEventsIdempotentlyAndRefreshNaturalKeySummaries() throws Exception {
        ExpensePostedEvent first = expenseEvent("expense-event-1", "expense-idem-1", 9001L, new BigDecimal("120.50"));
        ExpensePostedEvent second = expenseEvent("expense-event-2", "expense-idem-2", 9002L, new BigDecimal("79.50"));

        reportService.ingestExpensePosted(objectMapper.writeValueAsString(first), first);
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(first), first);
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(second), second);

        Integer expenseFactCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.expense_fact", Integer.class);
        Integer landingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_events.event_landing", Integer.class);
        Integer regionSummaryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.region_daily_summary", Integer.class);
        BigDecimal regionTotalExpense = jdbcTemplate.queryForObject(
                "SELECT total_expense FROM report.region_daily_summary WHERE region_id = 1 AND business_date = DATE '2026-03-27'",
                BigDecimal.class
        );
        BigDecimal companyTotalExpense = jdbcTemplate.queryForObject(
                "SELECT total_expense FROM report.company_daily_summary WHERE business_date = DATE '2026-03-27'",
                BigDecimal.class
        );
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT transaction_count FROM report.region_daily_summary WHERE region_id = 1 AND business_date = DATE '2026-03-27'",
                Long.class
        );

        assertThat(expenseFactCount).isEqualTo(2);
        assertThat(landingCount).isEqualTo(2);
        assertThat(regionSummaryCount).isEqualTo(1);
        assertThat(regionTotalExpense).isEqualByComparingTo("200.00");
        assertThat(companyTotalExpense).isEqualByComparingTo("200.00");
        assertThat(transactionCount).isEqualTo(2L);
    }

    @Test
    void shouldCreateAndProcessExpenseExportAsyncIdempotently() throws Exception {
        ExpensePostedEvent expense = expenseEvent("expense-event-export", "expense-idem-export", 9100L, new BigDecimal("88.00"));
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(expense), expense);

        String response = mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-expense-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(response).get("exportJobId").asLong();

        String secondResponse = mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-expense-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(secondResponse).get("exportJobId").asLong()).isEqualTo(jobId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.export_job", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report.outbox_event
                WHERE event_type = 'audit.event'
                  AND payload::text LIKE '%report.export.requested%'
                """, Integer.class)).isEqualTo(1);

        reportService.processQueuedExports();
        reportService.processQueuedExports();

        mockMvc.perform(get("/reports/exports/{jobId}", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.preview[0].expense_record_id").value(9100));

        mockMvc.perform(get("/reports/exports/{jobId}/preview", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].expense_record_id").value(9100));

        String csv = mockMvc.perform(get("/reports/exports/{jobId}/download", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("expense_record_id");
        assertThat(csv).contains("9100");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report.export_job WHERE export_job_id = ?", String.class, jobId))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report.outbox_event
                WHERE event_type = 'audit.event'
                  AND payload::text LIKE '%report.export.completed%'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldDenyRegionExportForEmptyNonSystemScope() throws Exception {
        mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(), false))
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowLegacyPayrollPermissionsForPayrollExportAdapter() throws Exception {
        mockMvc.perform(post("/reports/payroll/export")
                        .header("Authorization", bearer(Set.of(
                                PermissionCodes.REPORT_PAYROLL_READ,
                                PermissionCodes.REPORT_PAYROLL_EXPORT
                        ), List.of(1L), false))
                        .header("Idempotency-Key", "legacy-payroll-export")
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "fromDate": "2026-03-01",
                                  "toDate": "2026-03-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset").value("PAYROLL_SUMMARY"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void shouldSanitizeExportFailureErrorMessage() {
        jdbcTemplate.update("""
                INSERT INTO report.export_job (
                    export_job_id, idempotency_key, report_type, format, status, requested_by, requested_at, payload
                ) VALUES (
                    ?, ?, ?, ?, 'QUEUED', ?, CURRENT_TIMESTAMP, CAST(? AS jsonb)
                )
                """,
                90001L,
                "export-failure-sanitize",
                "PRIVATE_DATASET",
                "CSV",
                "system",
                """
                        {
                          "dataset": "EXPENSE_FACT",
                          "format": "CSV",
                          "regionId": 1,
                          "fromDate": "2026-03-27",
                          "toDate": "2026-03-27"
                        }
                        """
        );

        reportService.processQueuedExports();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM report.export_job WHERE export_job_id = ?",
                String.class,
                90001L
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_message FROM report.export_job WHERE export_job_id = ?",
                String.class,
                90001L
        )).isEqualTo("BadRequestException");
    }

    private ExpensePostedEvent expenseEvent(String eventId, String idempotencyKey, Long expenseRecordId, BigDecimal amount) {
        return new ExpensePostedEvent(
                eventId,
                "finance.expense.posted",
                Instant.parse("2026-03-27T08:00:00Z"),
                "finance-service",
                "corr-" + eventId,
                idempotencyKey,
                expenseRecordId,
                1L,
                101L,
                501L,
                7001L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                amount,
                "PAYROLL_RUN",
                "7001"
        );
    }

    private Set<String> reportPermissions() {
        return Set.of(PermissionCodes.REPORT_READ, PermissionCodes.REPORT_EXPORT);
    }

    private String bearer(Set<String> permissions, List<Long> regionIds, boolean systemScoped) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        long sequence = TOKEN_SEQUENCE.incrementAndGet();
        return "Bearer " + jwtService.encode(new FernJwtClaims(
                1000L + sequence,
                "report-tester-" + sequence,
                Set.of("finance"),
                permissions,
                new ScopeRoots(systemScoped, regionIds, List.of()),
                1L,
                1L,
                "report-test-jti-" + sequence,
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
    }

    private static Path createExportDir() {
        try {
            return Files.createTempDirectory("fern-report-exports-test");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create export temp directory", exception);
        }
    }
}
