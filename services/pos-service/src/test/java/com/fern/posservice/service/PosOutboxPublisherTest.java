package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fern.posservice.config.PosOutboxProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PosOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PosOutboxPublisher posOutboxPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        PosOutboxProperties properties = new PosOutboxProperties();
        properties.setMaxAttempts(3);
        posOutboxPublisher = new PosOutboxPublisher(jdbcTemplate, kafkaTemplate, properties, clock);
    }

    @Test
    void shouldMarkEventPublishedAfterKafkaAck() {
        PendingEventFixture event = pendingEvent(0);
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> java.util.List.of(event.toPendingEvent()));
        when(kafkaTemplate.send(event.eventType, event.partitionKey, event.payload))
                .thenReturn(CompletableFuture.completedFuture(null));

        posOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        org.mockito.Mockito.verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("SET status = :status"), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo(PosOutboxStatus.PUBLISHED.name());
    }

    @Test
    void shouldMarkEventFailedAfterMaxAttempts() {
        PendingEventFixture event = pendingEvent(2);
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(jdbcTemplate.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(invocation -> java.util.List.of(event.toPendingEvent()));
        when(kafkaTemplate.send(event.eventType, event.partitionKey, event.payload))
                .thenReturn(CompletableFuture.failedFuture(failure));

        posOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        org.mockito.Mockito.verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("retry_count = :retryCount"), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo(PosOutboxStatus.FAILED.name());
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(3);
    }

    private PendingEventFixture pendingEvent(int retryCount) {
        return new PendingEventFixture(
                UUID.randomUUID().toString(),
                PosEventTypes.SALE_COMPLETED,
                "101",
                "{\"id\":10}",
                retryCount
        );
    }

    private record PendingEventFixture(String id, String eventType, String partitionKey, String payload, int retryCount) {
        private Object toPendingEvent() {
            return new java.lang.Record() {
            };
        }
    }
}
