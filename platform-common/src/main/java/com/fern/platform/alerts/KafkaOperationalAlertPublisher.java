package com.fern.platform.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.OperationalAlertEvent;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaOperationalAlertPublisher implements OperationalAlertPublisher {
    public static final String OPS_ALERT_TOPIC = "ops.alert";

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaOperationalAlertPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String sourceService;

    public KafkaOperationalAlertPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            String sourceService
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.sourceService = sourceService;
    }

    @Override
    public void publish(
            String alertType,
            String severity,
            String summary,
            String correlationId,
            Long regionId,
            Long outletId,
            String entityType,
            String entityId,
            Map<String, Object> details
    ) {
        OperationalAlertEvent event = new OperationalAlertEvent(
                UUID.randomUUID().toString(),
                "ops.alert.raised",
                clock.instant(),
                sourceService,
                correlationId,
                UUID.randomUUID().toString(),
                alertType,
                severity,
                summary,
                regionId,
                outletId,
                entityType,
                entityId,
                details
        );
        try {
            String payload = objectMapper.writeValueAsString(event);
            String partitionKey = outletId != null ? outletId.toString()
                    : regionId != null ? regionId.toString()
                    : entityId != null ? entityId
                    : sourceService;
            kafkaTemplate.send(OPS_ALERT_TOPIC, partitionKey, payload);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("ops_alert_serialize_failed sourceService={} alertType={} message={}", sourceService, alertType, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.warn("ops_alert_publish_failed sourceService={} alertType={} message={}", sourceService, alertType, exception.getMessage());
        }
    }
}
