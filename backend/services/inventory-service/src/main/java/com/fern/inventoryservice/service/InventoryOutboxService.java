package com.fern.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InventoryOutboxService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    InventoryOutboxService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryRepository inventoryRepository,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryRepository = inventoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        String payloadJson = toJson(payload);
        lockOutboxKey(aggregateType, aggregateId, eventType);
        OutboxEventRecord existing = findOutboxEvent(aggregateType, aggregateId, eventType);
        if (existing != null) {
            requireMatchingOutboxPayload(existing, partitionKey, payloadJson);
            if ("FAILED".equals(existing.status())) {
                reviveFailedOutboxEvent(existing.id());
            }
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO inventory.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, retry_count, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', 0, CURRENT_TIMESTAMP
                )
                """, inventoryRepository.params(
                "id", UUID.randomUUID(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "partitionKey", partitionKey,
                "payload", payloadJson
        ));
    }

    private void lockOutboxKey(String aggregateType, String aggregateId, String eventType) {
        String lockKey = aggregateType + ":" + aggregateId + ":" + eventType;
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                inventoryRepository.params("lockKey", lockKey),
                rs -> null
        );
    }

    private OutboxEventRecord findOutboxEvent(String aggregateType, String aggregateId, String eventType) {
        return jdbcTemplate.query("""
                SELECT id::text AS id,
                       aggregate_type,
                       aggregate_id,
                       event_type,
                       partition_key,
                       payload::text AS payload,
                       status
                FROM inventory.outbox_event
                WHERE aggregate_type = :aggregateType
                  AND aggregate_id = :aggregateId
                  AND event_type = :eventType
                ORDER BY created_at, id
                LIMIT 1
                """, inventoryRepository.params(
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType
        ), rs -> rs.next()
                ? new OutboxEventRecord(
                        rs.getString("id"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("partition_key"),
                        rs.getString("payload"),
                        rs.getString("status"))
                : null);
    }

    private void reviveFailedOutboxEvent(String id) {
        jdbcTemplate.update("""
                UPDATE inventory.outbox_event
                SET status = 'PENDING',
                    retry_count = 0,
                    last_attempt_at = NULL,
                    last_error = NULL,
                    published_at = NULL
                WHERE id = CAST(:id AS uuid)
                """, inventoryRepository.params("id", id));
    }

    private void requireMatchingOutboxPayload(OutboxEventRecord existing, String partitionKey, String payloadJson) {
        if (!Objects.equals(existing.partitionKey(), partitionKey)
                || !jsonEqualsIgnoringEnvelope(existing.payload(), payloadJson)) {
            throw new IllegalStateException("Inventory outbox idempotency conflict");
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private boolean jsonEqualsIgnoringEnvelope(String left, String right) {
        try {
            var leftNode = objectMapper.readTree(left);
            var rightNode = objectMapper.readTree(right);
            if (leftNode.isObject()) {
                leftNode = leftNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) leftNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            if (rightNode.isObject()) {
                rightNode = rightNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) rightNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            return Objects.equals(leftNode, rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to compare inventory outbox payload", exception);
        }
    }

    private record OutboxEventRecord(
            String id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String partitionKey,
            String payload,
            String status
    ) {
    }
}
