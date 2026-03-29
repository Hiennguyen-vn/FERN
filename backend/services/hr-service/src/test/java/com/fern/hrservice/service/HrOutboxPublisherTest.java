package com.fern.hrservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
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

class HrOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

    private HrOutboxPublisher hrOutboxPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        hrOutboxPublisher = new HrOutboxPublisher(
                jdbcTemplate,
                kafkaTemplate,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                operationalAlertPublisher,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldMarkEventPublishedAfterKafkaAck() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = pendingEvent(0);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        hrOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("publishedStatus")).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldKeepEventPendingWhenKafkaSendFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = pendingEvent(0);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        hrOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo("PENDING");
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(1);
    }

    @Test
    void shouldMarkEventFailedAfterMaxAttempts() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = pendingEvent(2);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        hrOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo("FAILED");
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(3);
    }

    @Test
    void shouldKeepPublishingRemainingEventsAfterTerminalFailure() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = pendingEvent(2);
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SHIFT_ASSIGNMENT",
                "11",
                "hr.attendance.recorded",
                "101",
                "{\"id\":11}",
                0
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(failedEvent, nextEvent));
        when(kafkaTemplate.send(failedEvent.eventType(), failedEvent.partitionKey(), failedEvent.payload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));
        when(kafkaTemplate.send(nextEvent.eventType(), nextEvent.partitionKey(), nextEvent.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        hrOutboxPublisher.publishPending();

        verify(kafkaTemplate).send(nextEvent.eventType(), nextEvent.partitionKey(), nextEvent.payload());
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void shouldKeepPublishingRemainingEventsWhenAlertPublishingFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = pendingEvent(2);
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SHIFT_ASSIGNMENT",
                "12",
                "hr.attendance.recorded",
                "101",
                "{\"id\":12}",
                0
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(failedEvent, nextEvent));
        when(kafkaTemplate.send(failedEvent.eventType(), failedEvent.partitionKey(), failedEvent.payload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));
        when(kafkaTemplate.send(nextEvent.eventType(), nextEvent.partitionKey(), nextEvent.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));
        doThrow(new RuntimeException("alert unavailable")).when(operationalAlertPublisher).publish(
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyMap()
        );

        hrOutboxPublisher.publishPending();

        verify(kafkaTemplate).send(nextEvent.eventType(), nextEvent.partitionKey(), nextEvent.payload());
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    private JdbcOutboxPublisherSupport.ClaimedOutboxEvent pendingEvent(int retryCount) {
        return new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SHIFT_ASSIGNMENT",
                "10",
                "hr.attendance.recorded",
                "101",
                "{\"id\":10}",
                retryCount
        );
    }
}
