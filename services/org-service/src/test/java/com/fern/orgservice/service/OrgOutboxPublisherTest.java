package com.fern.orgservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.orgservice.domain.OutboxEventEntity;
import com.fern.orgservice.repository.OrgOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;

class OrgOutboxPublisherTest {
    @Mock
    private OrgOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OrgOutboxPublisher orgOutboxPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orgOutboxPublisher = new OrgOutboxPublisher(outboxRepository, kafkaTemplate, clock);
    }

    @Test
    void shouldMarkEventPublishedAfterKafkaAck() {
        OutboxEventEntity event = pendingEvent();
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        orgOutboxPublisher.publishPending();

        assertThat(event.getStatus()).isEqualTo("PUBLISHED");
        assertThat(event.getPublishedAt()).isEqualTo(clock.instant());
        verify(kafkaTemplate).send(event.getEventType(), event.getPartitionKey(), event.getPayload());
    }

    @Test
    void shouldKeepEventPendingWhenKafkaSendFails() {
        OutboxEventEntity event = pendingEvent();
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> orgOutboxPublisher.publishPending())
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);

        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPublishedAt()).isNull();
    }

    private OutboxEventEntity pendingEvent() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setAggregateType("region");
        event.setAggregateId("10");
        event.setEventType("org.region.changed");
        event.setPartitionKey("10");
        event.setPayload("{\"id\":10}");
        event.setStatus("PENDING");
        event.setCreatedAt(Instant.parse("2026-03-27T11:59:00Z"));
        return event;
    }
}
