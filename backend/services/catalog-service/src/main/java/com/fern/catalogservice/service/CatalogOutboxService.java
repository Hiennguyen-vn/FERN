package com.fern.catalogservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.catalogservice.domain.CatalogOutboxEventEntity;
import com.fern.catalogservice.repository.CatalogOutboxRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogOutboxService {
    private final CatalogOutboxRepository catalogOutboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CatalogOutboxService(CatalogOutboxRepository catalogOutboxRepository, ObjectMapper objectMapper, Clock clock) {
        this.catalogOutboxRepository = catalogOutboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        CatalogOutboxEventEntity entity = new CatalogOutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPartitionKey(partitionKey);
        entity.setPayload(toJson(payload));
        entity.setStatus("PENDING");
        entity.setCreatedAt(clock.instant());
        catalogOutboxRepository.save(entity);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox payload", exception);
        }
    }
}
