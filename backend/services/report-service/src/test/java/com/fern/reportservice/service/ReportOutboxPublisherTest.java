package com.fern.reportservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class ReportOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

    @Mock
    private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    private ReportOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        publisher = new ReportOutboxPublisher(
                jdbcTemplate,
                kafkaTemplateProvider,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                operationalAlertPublisher,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldKeepPublishingRemainingEventsWhenAlertPublishingFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = claimedEvent(2, "7701");
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = claimedEvent(0, "7702");
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

        publisher.publishPending();

        verify(kafkaTemplate).send(nextEvent.eventType(), nextEvent.partitionKey(), nextEvent.payload());
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void shouldSkipPublishingWhenKafkaTemplateIsUnavailable() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(null);
        ReportOutboxPublisher publisherWithoutKafka = new ReportOutboxPublisher(
                jdbcTemplate,
                kafkaTemplateProvider,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                operationalAlertPublisher,
                new SimpleMeterRegistry()
        );

        publisherWithoutKafka.publishPending();

        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    private JdbcOutboxPublisherSupport.ClaimedOutboxEvent claimedEvent(int retryCount, String aggregateId) {
        return new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "EXPORT_JOB",
                aggregateId,
                "report.export.requested",
                "101",
                "{\"id\":%s}".formatted(aggregateId),
                retryCount
        );
    }
}
