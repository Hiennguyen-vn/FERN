package com.fern.apigateway.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

class GatewayOutboxPublisherTest {
    @Mock
    private GatewayAuditOutboxStore gatewayAuditOutboxStore;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

    private GatewayOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new GatewayOutboxPublisher(
                gatewayAuditOutboxStore,
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
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent failedEvent = claimedEvent(2, "trace-10");
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent nextEvent = claimedEvent(0, "trace-11");
        when(gatewayAuditOutboxStore.claimBatch(any(), any(), anyInt()))
                .thenReturn(List.of(failedEvent, nextEvent));
        when(kafkaTemplate.send(argThat((ProducerRecord<String, String> record) ->
                record != null
                        && failedEvent.eventType().equals(record.topic())
                        && failedEvent.partitionKey().equals(record.key())
                        && failedEvent.payload().equals(record.value()))))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));
        when(kafkaTemplate.send(argThat((ProducerRecord<String, String> record) ->
                record != null
                        && nextEvent.eventType().equals(record.topic())
                        && nextEvent.partitionKey().equals(record.key())
                        && nextEvent.payload().equals(record.value()))))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(gatewayAuditOutboxStore.markFailed(any(), any(), anyInt(), anyString()))
                .thenReturn(new JdbcOutboxPublisherSupport.FailureOutcome(true, 3, "CompletionException -> RuntimeException"));
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

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> record) ->
                record != null
                        && nextEvent.eventType().equals(record.topic())
                        && nextEvent.partitionKey().equals(record.key())
                        && nextEvent.payload().equals(record.value())));
        verify(gatewayAuditOutboxStore).markPublished(nextEvent.id(), Instant.parse("2026-03-27T12:00:00Z"));
        verify(gatewayAuditOutboxStore).markFailed(any(), any(), anyInt(), anyString());
    }

    private JdbcOutboxPublisherSupport.ClaimedOutboxEvent claimedEvent(int retryCount, String aggregateId) {
        return new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "REQUEST_TRACE",
                aggregateId,
                "request.trace",
                aggregateId,
                "{\"requestId\":\"%s\"}".formatted(aggregateId),
                retryCount
        );
    }
}
