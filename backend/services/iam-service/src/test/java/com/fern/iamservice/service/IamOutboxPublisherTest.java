package com.fern.iamservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

class IamOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

    private IamOutboxPublisher iamOutboxPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        iamOutboxPublisher = new IamOutboxPublisher(
                jdbcTemplate,
                kafkaTemplate,
                clock,
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        iamOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("publishedStatus")).isEqualTo("PUBLISHED");
        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> r) ->
                r.topic().equals(event.eventType())
                        && r.key().equals(event.partitionKey())
                        && r.value().equals(event.payload())));
    }

    @Test
    void shouldKeepEventPendingWhenKafkaSendFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = pendingEvent(0);
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));

        iamOutboxPublisher.publishPending();

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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        iamOutboxPublisher.publishPending();

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
                "role",
                "11",
                "iam.role.changed",
                "11",
                "{\"id\":11}",
                0
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(failedEvent, nextEvent));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            if (record.topic().equals(failedEvent.eventType()) && record.value().equals(failedEvent.payload())) {
                return CompletableFuture.failedFuture(new RuntimeException("kafka unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        });

        iamOutboxPublisher.publishPending();

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> r) ->
                r.topic().equals(nextEvent.eventType())
                        && r.key().equals(nextEvent.partitionKey())
                        && r.value().equals(nextEvent.payload())));
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void shouldKeepPublishingRemainingEventsWhenAlertPublishingFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = pendingEvent(2);
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "role",
                "12",
                "iam.role.changed",
                "12",
                "{\"id\":12}",
                0
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(failedEvent, nextEvent));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            if (record.topic().equals(failedEvent.eventType()) && record.value().equals(failedEvent.payload())) {
                return CompletableFuture.failedFuture(new RuntimeException("kafka unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        });
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

        iamOutboxPublisher.publishPending();

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> r) ->
                r.topic().equals(nextEvent.eventType())
                        && r.key().equals(nextEvent.partitionKey())
                        && r.value().equals(nextEvent.payload())));
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    private JdbcOutboxPublisherSupport.ClaimedOutboxEvent pendingEvent(int retryCount) {
        return new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "role",
                "10",
                "iam.role.changed",
                "10",
                "{\"id\":10}",
                retryCount
        );
    }
}
