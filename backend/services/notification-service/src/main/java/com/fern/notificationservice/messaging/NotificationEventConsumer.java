package com.fern.notificationservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.notificationservice.service.NotificationService;
import com.fern.platform.contracts.OperationalAlertEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "ops.alert", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeOperationalAlert(String payload) {
        notificationService.ingestOperationalAlert(payload, read(payload, OperationalAlertEvent.class));
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
            throw new IllegalArgumentException("Unable to deserialize " + type.getSimpleName(), exception);
        }
    }
}
