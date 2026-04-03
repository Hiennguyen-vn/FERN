package com.fern.reportservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.testsupport.FernIntegrationContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ReportOutboxPublisherIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-03-27T12:00:00Z");
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
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "report-outbox-publisher-secret-01234567890123456789012");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.report.export.base-dir", () -> EXPORT_DIR.toString());
        registry.add("fern.report.export.preview-row-limit", () -> "5");
        registry.add("fern.report.export.worker-delay-ms", () -> "60000");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ReportOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jdbcTemplate.execute("""
                TRUNCATE TABLE report.outbox_event
                """);
        @SuppressWarnings("unchecked")
        ObjectProvider<KafkaTemplate<String, String>> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(kafkaTemplate);
        publisher = new ReportOutboxPublisher(
                namedParameterJdbcTemplate,
                provider,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldReclaimStaleInProgressReportOutboxEvent() {
        UUID eventId = insertOutbox("IN_PROGRESS", 1, NOW.minus(Duration.ofMinutes(2)));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> record) ->
                com.fern.platform.testsupport.JsonTestSupport.matchesProducerRecord(
                        record,
                        "report.export.requested",
                        "101",
                        "{\"id\":7701}")));
        assertThat(status(eventId)).isEqualTo("PUBLISHED");
        assertThat(publishedAt(eventId)).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(lastError(eventId)).isNull();
    }

    @Test
    void shouldNotReclaimFreshInProgressReportOutboxEvent() {
        UUID eventId = insertOutbox("IN_PROGRESS", 1, NOW.minusSeconds(30));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        assertThat(status(eventId)).isEqualTo("IN_PROGRESS");
        assertThat(publishedAt(eventId)).isNull();
    }

    @Test
    void shouldNotReclaimTerminalFailedReportOutboxEvent() {
        UUID eventId = insertOutbox("FAILED", 3, NOW.minus(Duration.ofMinutes(5)));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        assertThat(status(eventId)).isEqualTo("FAILED");
        assertThat(publishedAt(eventId)).isNull();
        assertThat(lastError(eventId)).isEqualTo("previous failure");
    }

    private UUID insertOutbox(String status, int retryCount, Instant lastAttemptAt) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO report.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status,
                    retry_count, created_at, last_attempt_at, last_error
                ) VALUES (
                    ?, 'EXPORT_JOB', '7701', 'report.export.requested', '101', CAST(? AS jsonb), ?,
                    ?, ?, ?, ?
                )
                """,
                eventId,
                "{\"id\":7701}",
                status,
                retryCount,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                lastAttemptAt == null ? null : OffsetDateTime.ofInstant(lastAttemptAt, ZoneOffset.UTC),
                "previous failure"
        );
        return eventId;
    }

    private String status(UUID eventId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM report.outbox_event
                WHERE id = ?
                """, String.class, eventId);
    }

    private OffsetDateTime publishedAt(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT published_at
                FROM report.outbox_event
                WHERE id = ?
                """, rs -> rs.next() ? rs.getObject("published_at", OffsetDateTime.class) : null, eventId);
    }

    private String lastError(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT last_error
                FROM report.outbox_event
                WHERE id = ?
                """, rs -> rs.next() ? rs.getString("last_error") : null, eventId);
    }

    private static Path createExportDir() {
        try {
            return Files.createTempDirectory("fern-report-outbox-publisher-test");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to create report export temp directory", exception);
        }
    }
}
