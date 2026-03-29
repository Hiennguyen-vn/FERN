package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.posservice.config.PosOutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
class PosOutboxPublisherIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-03-27T12:00:00Z");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("pos"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "pos-outbox-publisher-secret-012345678901234567890123");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.clients.catalog.base-url", () -> "http://localhost");
        registry.add("fern.clients.catalog.connect-timeout", () -> "500ms");
        registry.add("fern.clients.catalog.read-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost");
        registry.add("fern.clients.inventory.connect-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.read-timeout", () -> "500ms");
        registry.add("fern.clients.org.base-url", () -> "http://localhost");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PosOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jdbcTemplate.execute("""
                TRUNCATE TABLE pos.outbox_event
                RESTART IDENTITY CASCADE
                """);
        PosOutboxProperties properties = new PosOutboxProperties();
        properties.setMaxAttempts(3);
        properties.setReclaimAfter(Duration.ofMinutes(1));
        publisher = new PosOutboxPublisher(
                namedParameterJdbcTemplate,
                kafkaTemplate,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldReclaimStaleInProgressPosOutboxEvent() {
        UUID eventId = insertOutbox("IN_PROGRESS", 1, NOW.minus(Duration.ofMinutes(2)));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafkaTemplate).send(eq("pos.sale.completed"), eq("101"), anyString());
        assertThat(status(eventId)).isEqualTo("PUBLISHED");
        assertThat(publishedAt(eventId)).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(lastError(eventId)).isNull();
    }

    @Test
    void shouldNotReclaimFreshInProgressPosOutboxEvent() {
        UUID eventId = insertOutbox("IN_PROGRESS", 1, NOW.minusSeconds(30));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        assertThat(status(eventId)).isEqualTo("IN_PROGRESS");
        assertThat(publishedAt(eventId)).isNull();
    }

    @Test
    void shouldNotReclaimTerminalFailedPosOutboxEvent() {
        UUID eventId = insertOutbox("FAILED", 3, NOW.minus(Duration.ofMinutes(5)));

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        assertThat(status(eventId)).isEqualTo("FAILED");
        assertThat(publishedAt(eventId)).isNull();
        assertThat(lastError(eventId)).isEqualTo("previous failure");
    }

    private UUID insertOutbox(String status, int retryCount, Instant lastAttemptAt) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pos.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status,
                    retry_count, created_at, last_attempt_at, last_error
                ) VALUES (
                    ?, 'SALE_ORDER', '8801', 'pos.sale.completed', '101', CAST(? AS jsonb), ?,
                    ?, ?, ?, ?
                )
                """,
                eventId,
                "{\"saleOrderId\":8801,\"outletId\":101}",
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
                FROM pos.outbox_event
                WHERE id = ?
                """, String.class, eventId);
    }

    private OffsetDateTime publishedAt(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT published_at
                FROM pos.outbox_event
                WHERE id = ?
                """, rs -> rs.next() ? rs.getObject("published_at", OffsetDateTime.class) : null, eventId);
    }

    private String lastError(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT last_error
                FROM pos.outbox_event
                WHERE id = ?
                """, rs -> rs.next() ? rs.getString("last_error") : null, eventId);
    }
}
