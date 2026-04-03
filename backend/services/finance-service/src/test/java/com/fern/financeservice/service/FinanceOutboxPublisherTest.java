package com.fern.financeservice.service;

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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

class FinanceOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

    private FinanceOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new FinanceOutboxPublisher(
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
    void shouldKeepPublishingRemainingEventsWhenAlertPublishingFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = claimedEvent(2, "8801");
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = claimedEvent(0, "8802");
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

    private JdbcOutboxPublisherSupport.ClaimedOutboxEvent claimedEvent(int retryCount, String aggregateId) {
        return new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "EXPENSE_RECORD",
                aggregateId,
                "finance.expense.posted",
                "1",
                "{\"id\":%s}".formatted(aggregateId),
                retryCount
        );
    }
}
