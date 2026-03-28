package com.fern.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.notificationservice.config.NotificationProperties;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.OperationalAlertEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class NotificationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final NotificationProperties properties;
    private final Clock clock;
    private final Counter webhookFailureCounter;
    private final Counter dlqMessagesCounter;

    public NotificationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            SnowflakeIdGenerator idGenerator,
            ObjectMapper objectMapper,
            RestClient restClient,
            NotificationProperties properties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
        this.webhookFailureCounter = Counter.builder("fern_notification_webhook_failures_total").register(meterRegistry);
        this.dlqMessagesCounter = Counter.builder("fern_dlq_messages_total").register(meterRegistry);
    }

    @PostConstruct
    @Transactional
    public void bootstrapWebhookEndpoint() {
        if (properties.getOpsWebhook().getUrl() == null || properties.getOpsWebhook().getUrl().isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO notification.webhook_endpoint (
                    webhook_endpoint_id, endpoint_url, event_types, secret_key_ref, status, created_at, updated_at
                )
                SELECT :webhookEndpointId, :endpointUrl, CAST(:eventTypes AS jsonb), :secretKeyRef, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                    SELECT 1 FROM notification.webhook_endpoint WHERE endpoint_url = :endpointUrl
                )
                """, params(
                "webhookEndpointId", 1L,
                "endpointUrl", properties.getOpsWebhook().getUrl(),
                "eventTypes", toJson(properties.getDlqTopics()),
                "secretKeyRef", "ops-webhook"
        ));
    }

    @Transactional
    public void ingestOperationalAlert(String payload, OperationalAlertEvent event) {
        createNotificationJob(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "OPERATIONAL_ALERT",
                "WEBHOOK",
                event.summary(),
                payload,
                payload
        );
    }

    @Transactional
    public void ingestDlqMessage(String topic, int partition, long offset, String payload) {
        dlqMessagesCounter.increment();
        String idempotencyKey = "dlq:" + topic + ":" + partition + ":" + offset;
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("topic", topic);
        envelope.put("partition", partition);
        envelope.put("offset", offset);
        envelope.put("payload", payload);
        createNotificationJob(
                topic + ":" + partition + ":" + offset,
                "notification-service",
                "dlq.message.detected",
                clock.instant(),
                idempotencyKey,
                "DLQ_MESSAGE",
                "WEBHOOK",
                "DLQ message detected on " + topic,
                toJson(envelope),
                toJson(envelope)
        );
    }

    @Scheduled(fixedDelayString = "${fern.notification.retry.delay-ms:10000}")
    @Transactional
    public void deliverPending() {
        if (properties.getOpsWebhook().getUrl() == null || properties.getOpsWebhook().getUrl().isBlank()) {
            return;
        }
        jdbcTemplate.query("""
                SELECT notification_job_id, source_event_id, source_service, event_type, idempotency_key, subject, body, status
                FROM notification.notification_job
                WHERE status = 'PENDING'
                  AND (scheduled_at IS NULL OR scheduled_at <= CURRENT_TIMESTAMP)
                ORDER BY ingested_at, notification_job_id
                LIMIT 20
                """, params(), (rs, rowNum) -> new PendingNotification(
                rs.getLong("notification_job_id"),
                rs.getString("source_event_id"),
                rs.getString("source_service"),
                rs.getString("event_type"),
                rs.getString("idempotency_key"),
                rs.getString("subject"),
                rs.getString("body")
        )).forEach(notification -> deliverNotification(
                notification.notificationJobId(),
                notification.sourceEventId(),
                notification.sourceService(),
                notification.eventType(),
                notification.idempotencyKey(),
                notification.subject(),
                notification.body()
        ));
    }

    private void deliverNotification(
            long notificationJobId,
            String sourceEventId,
            String sourceService,
            String eventType,
            String idempotencyKey,
            String subject,
            String body
    ) {
        int attemptNumber = jdbcTemplate.query("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1
                FROM notification.delivery_attempt
                WHERE notification_job_id = :notificationJobId
                """, params("notificationJobId", notificationJobId), rs -> rs.next() ? rs.getInt(1) : 1);
        try {
            restClient.post()
                    .uri(properties.getOpsWebhook().getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Fern-Webhook-Secret", properties.getOpsWebhook().getSecret() == null ? "" : properties.getOpsWebhook().getSecret())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            recordAttempt(notificationJobId, attemptNumber, "SENT", null, "200");
            jdbcTemplate.update("""
                    UPDATE notification.notification_job
                    SET status = 'SENT',
                        sent_at = CURRENT_TIMESTAMP,
                        delivered_at = CURRENT_TIMESTAMP
                    WHERE notification_job_id = :notificationJobId
                      AND status = 'PENDING'
                    """, params("notificationJobId", notificationJobId));
            upsertWebhookLog(sourceEventId, sourceService, eventType, idempotencyKey, "DELIVERED", attemptNumber, body);
        } catch (RuntimeException exception) {
            webhookFailureCounter.increment();
            recordAttempt(notificationJobId, attemptNumber, "FAILED", exception.getMessage(), null);
            boolean terminalFailure = attemptNumber >= properties.getRetry().getMaxAttempts();
            jdbcTemplate.update("""
                    UPDATE notification.notification_job
                    SET status = :status,
                        scheduled_at = :scheduledAt,
                        delivered_at = CASE WHEN :terminalFailure THEN CURRENT_TIMESTAMP ELSE delivered_at END
                    WHERE notification_job_id = :notificationJobId
                      AND status = 'PENDING'
                    """, params(
                    "status", terminalFailure ? "FAILED" : "PENDING",
                    "scheduledAt", terminalFailure ? null : clock.instant().plusMillis(properties.getRetry().getDelayMs()),
                    "terminalFailure", terminalFailure,
                    "notificationJobId", notificationJobId
            ));
            upsertWebhookLog(sourceEventId, sourceService, eventType, idempotencyKey, terminalFailure ? "FAILED" : "RETRYING", attemptNumber, body);
        }
    }

    private void createNotificationJob(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String notificationType,
            String channel,
            String subject,
            String body,
            String payload
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification.notification_job (
                    notification_job_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    notification_type, channel, recipient, subject, body, status, scheduled_at, payload
                ) VALUES (
                    :notificationJobId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :notificationType, :channel, :recipient, :subject, :body, 'PENDING', CURRENT_TIMESTAMP, CAST(:payload AS jsonb)
                )
                ON CONFLICT DO NOTHING
                """, params(
                "notificationJobId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey,
                "notificationType", notificationType,
                "channel", channel,
                "recipient", properties.getOpsWebhook().getUrl(),
                "subject", subject,
                "body", body,
                "payload", payload
        ));
    }

    private void recordAttempt(long notificationJobId, int attemptNumber, String status, String errorMessage, String responseCode) {
        jdbcTemplate.update("""
                INSERT INTO notification.delivery_attempt (
                    delivery_attempt_id, notification_job_id, attempt_number, attempt_timestamp, status, error_message, response_code
                ) VALUES (
                    :deliveryAttemptId, :notificationJobId, :attemptNumber, CURRENT_TIMESTAMP, :status, :errorMessage, :responseCode
                )
                """, params(
                "deliveryAttemptId", idGenerator.nextId(),
                "notificationJobId", notificationJobId,
                "attemptNumber", attemptNumber,
                "status", status,
                "errorMessage", errorMessage,
                "responseCode", responseCode
        ));
    }

    private void upsertWebhookLog(
            String sourceEventId,
            String sourceService,
            String eventType,
            String idempotencyKey,
            String deliveryStatus,
            int attemptCount,
            String payload
    ) {
        jdbcTemplate.update("""
                INSERT INTO notification.webhook_delivery_log (
                    webhook_delivery_log_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    webhook_endpoint_id, delivery_status, attempt_count, last_attempt_at, delivered_at, payload
                ) VALUES (
                    :webhookDeliveryLogId, :sourceEventId, :sourceService, :eventType, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :idempotencyKey,
                    1, :deliveryStatus, :attemptCount, CURRENT_TIMESTAMP,
                    CASE WHEN :deliveryStatus = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CAST(:payload AS jsonb)
                )
                ON CONFLICT (source_event_id) DO UPDATE
                SET delivery_status = EXCLUDED.delivery_status,
                    attempt_count = EXCLUDED.attempt_count,
                    last_attempt_at = EXCLUDED.last_attempt_at,
                    delivered_at = EXCLUDED.delivered_at,
                    payload = EXCLUDED.payload
                """, params(
                "webhookDeliveryLogId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "idempotencyKey", idempotencyKey,
                "deliveryStatus", deliveryStatus,
                "attemptCount", attemptCount,
                "payload", payload
        ));
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value == null) {
                parameters.addValue((String) values[index], null, Types.NULL);
            } else if (value instanceof Instant instant) {
                parameters.addValue((String) values[index], OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private record PendingNotification(
            long notificationJobId,
            String sourceEventId,
            String sourceService,
            String eventType,
            String idempotencyKey,
            String subject,
            String body
    ) {
    }
}
