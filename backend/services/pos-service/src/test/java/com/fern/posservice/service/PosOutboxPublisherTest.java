package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.alerts.OperationalAlertPublisher;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

class PosOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

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
                operationalAlertPublisher,
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));

        publisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo(PosOutboxStatus.FAILED.name());
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(3);
    }

    @Test
    void shouldKeepEventPendingWhenKafkaSendFailsBelowMaxAttempts() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SALE_ORDER",
                "10",
                PosEventTypes.SALE_COMPLETED,
                "101",
                "{\"id\":10}",
                0
        );
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));

        publisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo(PosOutboxStatus.PENDING.name());
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(1);
    }

    @Test
    void shouldRequeueEventWhenKafkaSendSucceedsButMarkPublishedFails() {
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource parameters = invocation.getArgument(1);
                    if (parameters.hasValue("publishedStatus")) {
                        throw new RuntimeException("mark published failed");
                    }
                    return 1;
                });

        publisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(anyString(), parameters.capture());
        MapSqlParameterSource failureParameters = parameters.getAllValues().getLast();
        assertThat(failureParameters.getValue("status")).isEqualTo(PosOutboxStatus.PENDING.name());
        assertThat(failureParameters.getValue("retryCount")).isEqualTo(1);
    }

    @Test
    void shouldPublishTerminalAlertAgainstSaleOrderAggregateId() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SALE_ORDER",
                "10",
                PosEventTypes.SALE_COMPLETED,
                "101",
                "{\"id\":10}",
                2
        );
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        publisher.publishPending();

        verify(operationalAlertPublisher).publish(
                eq("OUTBOX_TERMINAL_FAILURE"),
                eq("HIGH"),
                eq("POS outbox publish failed for " + event.eventType()),
                any(),
                any(),
                any(),
                eq("SALE_ORDER"),
                eq("10"),
                anyMap()
        );
    }

    @Test
    void shouldKeepPublishingRemainingEventsWhenAlertPublishingFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SALE_ORDER",
                "10",
                PosEventTypes.SALE_COMPLETED,
                "101",
                "{\"id\":10}",
                2
        );
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "SALE_ORDER",
                "11",
                PosEventTypes.SALE_COMPLETED,
                "101",
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

        publisher.publishPending();

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> r) ->
                r.topic().equals(nextEvent.eventType())
                        && r.key().equals(nextEvent.partitionKey())
                        && r.value().equals(nextEvent.payload())));
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }
}
