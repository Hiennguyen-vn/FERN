package com.fern.reportservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.reportservice.messaging.ReportEventConsumer;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ReportEventLandingReplayIntegrationTest {
    private static final Path EXPORT_DIR = createExportDir();

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
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.report.export.base-dir", () -> EXPORT_DIR.toString());
        registry.add("fern.report.export.preview-row-limit", () -> "5");
        registry.add("fern.report.export.worker-delay-ms", () -> "60000");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportEventConsumer reportEventConsumer;

    @SpyBean
    private DailySummaryProjector dailySummaryProjector;

    @BeforeEach
    void setUp() {
        reset(dailySummaryProjector);
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    report.outbox_event,
                    report.company_daily_outlet,
                    report.region_daily_event,
                    report.export_job,
                    report.projection_watermark,
                    report.inventory_stock_snapshot,
                    report.payables_fact,
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
    }

    @Test
    void shouldPersistFailedLandingAndRecoverOnReplay() throws Exception {
        ExpensePostedEvent event = new ExpensePostedEvent(
                "expense-event-replay-failed",
                "finance.expense.posted",
                Instant.parse("2026-03-27T08:00:00Z"),
                "finance-service",
                "corr-expense-event-replay-failed",
                "expense-idem-replay-failed",
                9901L,
                1L,
                101L,
                501L,
                7001L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                new BigDecimal("42.25"),
                "PAYROLL_RUN",
                "7001"
        );
        String payload = objectMapper.writeValueAsString(event);
        AtomicBoolean failOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            if (failOnce.compareAndSet(true, false)) {
                throw new DataAccessResourceFailureException("forced report expense failure");
            }
            return invocation.callRealMethod();
        }).when(dailySummaryProjector).applyDelta(
                anyString(), anyString(), anyString(), any(), anyString(),
                anyLong(), anyList(), any(), anyString(), any()
        );

        assertThatThrownBy(() -> reportService.ingestExpensePosted(payload, event))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(landingStatus("expense-event-replay-failed")).isEqualTo("FAILED");
        assertThat(landingError("expense-event-replay-failed")).isEqualTo("DataAccessResourceFailureException");
        assertThat(count("SELECT COUNT(*) FROM raw_events.event_landing WHERE source_event_id = 'expense-event-replay-failed'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM report.expense_fact WHERE source_event_id = 'expense-event-replay-failed'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM report.region_daily_summary")).isZero();

        reportService.ingestExpensePosted(payload, event);

        assertThat(landingStatus("expense-event-replay-failed")).isEqualTo("PROCESSED");
        assertThat(landingProcessedAt("expense-event-replay-failed")).isNotNull();
        assertThat(landingError("expense-event-replay-failed")).isNull();
        assertThat(count("SELECT COUNT(*) FROM raw_events.event_landing WHERE source_event_id = 'expense-event-replay-failed'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM report.expense_fact WHERE source_event_id = 'expense-event-replay-failed'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT total_expense
                FROM report.region_daily_summary
                WHERE region_id = 1 AND business_date = DATE '2026-03-27'
                """, BigDecimal.class)).isEqualByComparingTo("42.25");
    }

    @Test
    void shouldPersistMalformedPayloadInLandingWhenDeserializationFails() {
        assertThatThrownBy(() -> reportEventConsumer.consumeExpensePosted("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to deserialize ExpensePostedEvent");

        assertThat(count("SELECT COUNT(*) FROM raw_events.event_landing WHERE kafka_topic = 'finance.expense.posted'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM raw_events.event_landing
                WHERE kafka_topic = 'finance.expense.posted'
                """, String.class)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT source_service
                FROM raw_events.event_landing
                WHERE kafka_topic = 'finance.expense.posted'
                """, String.class)).isEqualTo("report-service");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM raw_events.event_landing
                WHERE kafka_topic = 'finance.expense.posted'
                """, String.class)).contains("rawPayload")
                .contains("not-json");
    }

    @Test
    void shouldCompleteProjectionWhenLandingIsStuckInReceivedStatus() throws Exception {
        // Simulate crash between transaction 1 (beginLanding → RECEIVED) and
        // transaction 2 (work + markLandingProcessed). The record stays in RECEIVED.
        // On Kafka re-delivery, ingestWithLanding must detect RECEIVED and retry.
        ExpensePostedEvent event = new ExpensePostedEvent(
                "expense-event-received-stuck",
                "finance.expense.posted",
                Instant.parse("2026-03-27T11:00:00Z"),
                "finance-service",
                "corr-expense-event-received-stuck",
                "expense-idem-received-stuck",
                9904L,
                1L,
                101L,
                504L,
                7004L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                new BigDecimal("33.00"),
                "PAYROLL_RUN",
                "7004"
        );
        String payload = objectMapper.writeValueAsString(event);

        // Manually insert a RECEIVED landing row to simulate the crashed-between-transactions state
        jdbcTemplate.update("""
                INSERT INTO raw_events.event_landing (landing_id, source_event_id, source_service, event_type, occurred_at,
                    ingested_at, idempotency_key, kafka_topic, payload, status)
                VALUES (999999, 'expense-event-received-stuck', 'finance-service', 'finance.expense.posted',
                    '2026-03-27T11:00:00Z', CURRENT_TIMESTAMP, 'expense-idem-received-stuck',
                    'finance.expense.posted', CAST(? AS jsonb), 'RECEIVED')
                """, payload);

        assertThat(landingStatus("expense-event-received-stuck")).isEqualTo("RECEIVED");
        assertThat(count("SELECT COUNT(*) FROM report.expense_fact WHERE source_event_id = 'expense-event-received-stuck'")).isZero();

        // Re-delivery: should detect RECEIVED and complete the projection
        reportService.ingestExpensePosted(payload, event);

        assertThat(landingStatus("expense-event-received-stuck")).isEqualTo("PROCESSED");
        assertThat(count("SELECT COUNT(*) FROM report.expense_fact WHERE source_event_id = 'expense-event-received-stuck'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT total_expense
                FROM report.region_daily_summary
                WHERE region_id = 1 AND business_date = DATE '2026-03-27'
                """, BigDecimal.class)).isEqualByComparingTo("33.00");
    }

    @Test
    void shouldRejectConflictingReplayWhenIdempotencyKeyIsReusedWithDifferentPayload() throws Exception {
        ExpensePostedEvent first = new ExpensePostedEvent(
                "expense-event-conflict-1",
                "finance.expense.posted",
                Instant.parse("2026-03-27T09:00:00Z"),
                "finance-service",
                "corr-expense-event-conflict",
                "expense-idem-conflict",
                9902L,
                1L,
                101L,
                502L,
                7002L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                new BigDecimal("50.00"),
                "PAYROLL_RUN",
                "7002"
        );
        ExpensePostedEvent conflictingReplay = new ExpensePostedEvent(
                "expense-event-conflict-2",
                "finance.expense.posted",
                Instant.parse("2026-03-27T09:00:00Z"),
                "finance-service",
                "corr-expense-event-conflict",
                "expense-idem-conflict",
                9902L,
                1L,
                101L,
                502L,
                7002L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                new BigDecimal("75.00"),
                "PAYROLL_RUN",
                "7002"
        );

        reportService.ingestExpensePosted(objectMapper.writeValueAsString(first), first);

        assertThatThrownBy(() -> reportService.ingestExpensePosted(
                objectMapper.writeValueAsString(conflictingReplay),
                conflictingReplay
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Report landing idempotency conflict");

        assertThat(count("SELECT COUNT(*) FROM raw_events.event_landing WHERE idempotency_key = 'expense-idem-conflict'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM report.expense_fact WHERE idempotency_key = 'expense-idem-conflict'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT total_expense
                FROM report.region_daily_summary
                WHERE region_id = 1 AND business_date = DATE '2026-03-27'
                """, BigDecimal.class)).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldAllowReplayWhenLegacyPayloadDiffersOnlyByMissingEventVersion() throws Exception {
        ExpensePostedEvent event = new ExpensePostedEvent(
                "expense-event-version-compat",
                "finance.expense.posted",
                Instant.parse("2026-03-27T10:00:00Z"),
                "finance-service",
                "corr-expense-event-version-compat",
                "expense-idem-version-compat",
                9903L,
                1L,
                101L,
                503L,
                7003L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                new BigDecimal("15.75"),
                "PAYROLL_RUN",
                "7003"
        );
        ObjectNode legacyPayload = objectMapper.valueToTree(event);
        legacyPayload.remove("eventVersion");

        reportService.ingestExpensePosted(legacyPayload.toString(), event);
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(event), event);

        assertThat(count("SELECT COUNT(*) FROM raw_events.event_landing WHERE source_event_id = 'expense-event-version-compat'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM report.expense_fact WHERE source_event_id = 'expense-event-version-compat'")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT total_expense
                FROM report.region_daily_summary
                WHERE region_id = 1 AND business_date = DATE '2026-03-27'
                """, BigDecimal.class)).isEqualByComparingTo("15.75");
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String landingStatus(String sourceEventId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM raw_events.event_landing
                WHERE source_event_id = ?
                """, String.class, sourceEventId);
    }

    private String landingError(String sourceEventId) {
        return jdbcTemplate.queryForObject("""
                SELECT error_message
                FROM raw_events.event_landing
                WHERE source_event_id = ?
                """, String.class, sourceEventId);
    }

    private Instant landingProcessedAt(String sourceEventId) {
        return jdbcTemplate.queryForObject("""
                SELECT processed_at
                FROM raw_events.event_landing
                WHERE source_event_id = ?
                """, (rs, rowNum) -> {
            var value = rs.getObject(1, java.time.OffsetDateTime.class);
            return value == null ? null : value.toInstant();
        }, sourceEventId);
    }

    private static Path createExportDir() {
        try {
            return Files.createTempDirectory("fern-report-landing-test");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create report export temp directory", exception);
        }
    }
}
