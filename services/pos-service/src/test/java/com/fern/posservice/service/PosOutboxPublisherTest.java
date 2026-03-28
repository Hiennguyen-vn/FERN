package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import com.fern.posservice.config.PosOutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class PosOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PosOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        PosOutboxProperties properties = new PosOutboxProperties();
        properties.setMaxAttempts(3);
        publisher = new PosOutboxPublisher(
                jdbcTemplate,
                kafkaTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldMarkEventPublishedAfterKafkaAck() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SALE_ORDER",
                "10",
                PosEventTypes.SALE_COMPLETED,
                "101",
                "{\"id\":10}",
                0
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("publishedStatus")).isEqualTo(PosOutboxStatus.PUBLISHED.name());
    }

    @Test
    void shouldMarkEventFailedAfterMaxAttempts() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SALE_ORDER",
                "10",
                PosEventTypes.SALE_COMPLETED,
                "101",
                "{\"id\":10}",
                2
        );
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        publisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo(PosOutboxStatus.FAILED.name());
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(3);
    }
}
