package com.fern.iamservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.iamservice.domain.IamOutboxEventEntity;
import com.fern.iamservice.repository.IamOutboxRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamOutboxService {
    private final IamOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IamOutboxService(IamOutboxRepository outboxRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        IamOutboxEventEntity entity = new IamOutboxEventEntity();
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
            throw new IllegalStateException("Unable to serialize IAM outbox payload", exception);
        }
    }
}
