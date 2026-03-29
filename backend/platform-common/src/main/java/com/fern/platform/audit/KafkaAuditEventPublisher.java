package com.fern.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaAuditEventPublisher implements AuditEventPublisher {
    public static final String AUDIT_EVENT_TOPIC = "audit.event";
    public static final String SECURITY_EVENT_TOPIC = "audit.security";
    public static final String REQUEST_TRACE_TOPIC = "request.trace";

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaAuditEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaAuditEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishAuditEvent(AuditEvent event) {
        publish(AUDIT_EVENT_TOPIC, event.eventId(), event);
    }

    @Override
    public void publishSecurityEvent(SecurityEvent event) {
        publish(SECURITY_EVENT_TOPIC, event.eventId(), event);
    }

    @Override
    public void publishRequestTrace(RequestTraceEvent event) {
        publish(REQUEST_TRACE_TOPIC, event.eventId(), event);
    }

    private void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            LOGGER.warn("audit_publish_failed topic={} key={} message={}", topic, key, error.getMessage());
                        }
                    });
        } catch (JsonProcessingException exception) {
            LOGGER.warn("audit_serialize_failed topic={} key={} message={}", topic, key, exception.getMessage());
        }
    }
}
