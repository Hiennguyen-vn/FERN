package com.fern.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class JdbcAuditOutboxEventPublisher implements AuditEventPublisher {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String qualifiedOutboxTable;
    private final boolean payloadJsonb;

    public JdbcAuditOutboxEventPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            String qualifiedOutboxTable,
            boolean payloadJsonb
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.qualifiedOutboxTable = qualifiedOutboxTable;
        this.payloadJsonb = payloadJsonb;
    }

    @Override
    public void publishAuditEvent(AuditEvent event) {
        enqueue("AUDIT_EVENT", event.eventId(), KafkaAuditEventPublisher.AUDIT_EVENT_TOPIC, event);
    }

    @Override
    public void publishSecurityEvent(SecurityEvent event) {
        enqueue("SECURITY_EVENT", event.eventId(), KafkaAuditEventPublisher.SECURITY_EVENT_TOPIC, event);
    }

    @Override
    public void publishRequestTrace(RequestTraceEvent event) {
        enqueue("REQUEST_TRACE", event.eventId(), KafkaAuditEventPublisher.REQUEST_TRACE_TOPIC, event);
    }

    private void enqueue(String aggregateType, String aggregateId, String topic, Object event) {
        String payload = toJson(event);
        String payloadExpression = payloadJsonb ? "CAST(:payload AS jsonb)" : ":payload";
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, %s, 'PENDING', CURRENT_TIMESTAMP
                )
                """.formatted(qualifiedOutboxTable, payloadExpression), params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", topic,
                "partitionKey", aggregateId,
                "payload", payload
        ));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit payload", exception);
        }
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            parameters.addValue((String) values[index], values[index + 1]);
        }
        return parameters;
    }
}
