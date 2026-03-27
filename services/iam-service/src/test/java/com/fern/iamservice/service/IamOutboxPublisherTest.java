package com.fern.iamservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.iamservice.domain.IamOutboxEventEntity;
import com.fern.iamservice.repository.IamOutboxRepository;
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

class IamOutboxPublisherTest {
    @Mock
    private IamOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private IamOutboxPublisher iamOutboxPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        iamOutboxPublisher = new IamOutboxPublisher(outboxRepository, kafkaTemplate, clock);
    }

    @Test
    void shouldMarkEventPublishedAfterKafkaAck() {
        IamOutboxEventEntity event = pendingEvent();
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        iamOutboxPublisher.publishPending();

        assertThat(event.getStatus()).isEqualTo("PUBLISHED");
        assertThat(event.getPublishedAt()).isEqualTo(clock.instant());
        verify(kafkaTemplate).send(event.getEventType(), event.getPartitionKey(), event.getPayload());
    }

    @Test
    void shouldKeepEventPendingWhenKafkaSendFails() {
        IamOutboxEventEntity event = pendingEvent();
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> iamOutboxPublisher.publishPending())
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);

        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPublishedAt()).isNull();
    }

    private IamOutboxEventEntity pendingEvent() {
        IamOutboxEventEntity event = new IamOutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setAggregateType("role");
        event.setAggregateId("10");
        event.setEventType("iam.role.changed");
        event.setPartitionKey("10");
        event.setPayload("{\"id\":10}");
        event.setStatus("PENDING");
        event.setCreatedAt(Instant.parse("2026-03-27T11:59:00Z"));
        return event;
    }
}
