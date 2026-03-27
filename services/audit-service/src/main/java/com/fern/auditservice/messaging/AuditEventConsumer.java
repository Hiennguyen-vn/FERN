package com.fern.auditservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.auditservice.service.AuditIngestionService;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.audit.SecurityEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {
    private final ObjectMapper objectMapper;
    private final AuditIngestionService auditIngestionService;

    public AuditEventConsumer(ObjectMapper objectMapper, AuditIngestionService auditIngestionService) {
        this.objectMapper = objectMapper;
        this.auditIngestionService = auditIngestionService;
    }

    @KafkaListener(topics = "audit.event")
    public void consumeAuditEvent(String payload) {
        auditIngestionService.ingestAuditEvent(read(payload, AuditEvent.class));
    }

    @KafkaListener(topics = "audit.security")
    public void consumeSecurityEvent(String payload) {
        auditIngestionService.ingestSecurityEvent(read(payload, SecurityEvent.class));
    }

    @KafkaListener(topics = "request.trace")
    public void consumeRequestTrace(String payload) {
        auditIngestionService.ingestRequestTrace(read(payload, RequestTraceEvent.class));
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize " + type.getSimpleName(), exception);
        }
    }
}
