package com.fern.orgservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.orgservice.domain.OutboxEventEntity;
import com.fern.orgservice.repository.OrgOutboxRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrgOutboxService {
    private final OrgOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrgOutboxService(OrgOutboxRepository outboxRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPartitionKey(partitionKey);
        entity.setPayload(toJson(payload));
        entity.setStatus("PENDING");
        entity.setCreatedAt(clock.instant());
        outboxRepository.save(entity);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox payload", exception);
        }
    }
}
