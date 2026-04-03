package com.fern.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.testsupport.FernIntegrationContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class CatalogOutboxPublisherIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-03-27T12:00:00Z");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("catalog"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "catalog-outbox-publisher-secret-012345678901234567890");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private CatalogOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jdbcTemplate.execute("TRUNCATE TABLE catalog.outbox_event");
        publisher = new CatalogOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldReclaimStaleInProgressCatalogOutboxEvent() {
        UUID eventId = insertOutbox("IN_PROGRESS", 1, NOW.minus(Duration.ofMinutes(2)));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> record) ->
                com.fern.platform.testsupport.JsonTestSupport.matchesProducerRecord(
                        record,
                        "catalog.product.changed",
                        "55",
                        "{\"id\":55}")));
        assertThat(status(eventId)).isEqualTo("PUBLISHED");
        assertThat(publishedAt(eventId)).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(lastError(eventId)).isNull();
    }

    @Test
    void shouldNotReclaimFreshInProgressCatalogOutboxEvent() {
        UUID eventId = insertOutbox("IN_PROGRESS", 1, NOW.minusSeconds(30));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        assertThat(status(eventId)).isEqualTo("IN_PROGRESS");
        assertThat(publishedAt(eventId)).isNull();
    }

    @Test
    void shouldNotReclaimTerminalFailedCatalogOutboxEvent() {
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
                INSERT INTO catalog.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status,
                    retry_count, created_at, last_attempt_at, last_error
                ) VALUES (?, 'PRODUCT', '55', 'catalog.product.changed', '55', CAST(? AS jsonb), ?, ?, ?, ?, ?)
                """,
                eventId,
                "{\"id\":55}",
                status,
                retryCount,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                lastAttemptAt == null ? null : OffsetDateTime.ofInstant(lastAttemptAt, ZoneOffset.UTC),
                "previous failure"
        );
        return eventId;
    }

    private String status(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT status FROM catalog.outbox_event WHERE id = ?", String.class, eventId);
    }

    private OffsetDateTime publishedAt(UUID eventId) {
        return jdbcTemplate.query(
                "SELECT published_at FROM catalog.outbox_event WHERE id = ?",
                rs -> rs.next() ? rs.getObject("published_at", OffsetDateTime.class) : null,
                eventId
        );
    }

    private String lastError(UUID eventId) {
        return jdbcTemplate.query(
                "SELECT last_error FROM catalog.outbox_event WHERE id = ?",
                rs -> rs.next() ? rs.getString("last_error") : null,
                eventId
        );
    }
}
