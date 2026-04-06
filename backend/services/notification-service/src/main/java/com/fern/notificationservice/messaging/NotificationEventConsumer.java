package com.fern.notificationservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.notificationservice.service.NotificationService;
import com.fern.platform.contracts.OperationalAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationEventConsumer(
            ObjectMapper objectMapper, 
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "ops.alert", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeOperationalAlert(String payload) {
        notificationService.ingestOperationalAlert(payload, read(payload, OperationalAlertEvent.class));
    }

    @KafkaListener(topicPattern = "pos\\..*", groupId = "${spring.kafka.consumer.group-id}-ws")
    public void consumePosEvents(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key
    ) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String outletId = root.has("outletId") ? root.get("outletId").asText() : key;
            if (outletId != null && !outletId.isBlank()) {
                messagingTemplate.convertAndSend("/topic/pos/" + outletId, payload);
            }
        } catch (Exception e) {
            log.warn("NOTIFICATION_WS_FORWARD_FAILED topic={} key={}: {}", topic, key, e.getMessage());
        }
    }

    @KafkaListener(topics = "#{'${fern.notification.dlq-topics:__no_dlq__}'.split(',')}", groupId = "${spring.kafka.consumer.group-id}-dlq")
    public void consumeDlqMessage(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        if ("__no_dlq__".equals(topic)) {
            return;
        }
        notificationService.ingestDlqMessage(topic, partition, offset, payload);
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            // Log the failure before throwing — the DLQ handler will route the message
            log.error("DESERIALIZATION_FAILED type={}: {}", type.getSimpleName(), exception.getMessage());
            throw new IllegalArgumentException("Unable to deserialize " + type.getSimpleName(), exception);
        }
    }
}
