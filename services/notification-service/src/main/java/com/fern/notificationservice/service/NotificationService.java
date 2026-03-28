package com.fern.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.notificationservice.config.NotificationProperties;
import com.fern.platform.common.ExceptionSummaries;
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
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

@Service
public class NotificationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final NotificationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Counter webhookFailureCounter;
    private final Counter dlqMessagesCounter;

    public NotificationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            SnowflakeIdGenerator idGenerator,
            ObjectMapper objectMapper,
            RestClient restClient,
            NotificationProperties properties,
            Clock clock,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.webhookFailureCounter = Counter.builder("fern_notification_webhook_failures_total").register(meterRegistry);
        this.dlqMessagesCounter = Counter.builder("fern_dlq_messages_total").register(meterRegistry);
    }

    @PostConstruct
    @Transactional
    public void bootstrapWebhookEndpoint() {
        validateWebhookConfiguration();
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
                "webhookEndpointId", idGenerator.nextId(),
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
    public void deliverPending() {
        if (properties.getOpsWebhook().getUrl() == null || properties.getOpsWebhook().getUrl().isBlank()) {
            return;
        }
        claimPendingNotifications().forEach(this::deliverNotification);
    }

    private void deliverNotification(PendingNotification notification) {
        int attemptNumber = nextAttemptNumber(notification.notificationJobId());
        ResolvedWebhookEndpoint webhookEndpoint = null;
        try {
            webhookEndpoint = resolveWebhookEndpoint(notification.recipient());
            ResponseEntity<Void> response = restClient.post()
                    .uri(webhookEndpoint.endpointUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Fern-Webhook-Secret", webhookEndpoint.secret())
                    .header("X-Fern-Notification-Idempotency-Key", notification.idempotencyKey())
                    .header("X-Fern-Source-Event-Id", notification.sourceEventId())
                    .body(notification.body())
                    .retrieve()
                    .toBodilessEntity();
            recordSuccess(notification, webhookEndpoint.webhookEndpointId(), attemptNumber, String.valueOf(response.getStatusCode().value()));
        } catch (RuntimeException exception) {
            webhookFailureCounter.increment();
            String errorSummary = ExceptionSummaries.safeSummary(exception);
            boolean terminalFailure = attemptNumber >= properties.getRetry().getMaxAttempts();
            recordFailure(
                    notification,
                    webhookEndpoint == null ? null : webhookEndpoint.webhookEndpointId(),
                    attemptNumber,
                    errorSummary,
                    terminalFailure
            );
        }
    }

    private List<PendingNotification> claimPendingNotifications() {
        Instant now = clock.instant();
        Instant staleBefore = now.minusMillis(Math.max(properties.getRetry().getDelayMs(), 60000L));
        return transactionTemplate.execute(status -> jdbcTemplate.query("""
                UPDATE notification.notification_job job
                SET status = 'IN_PROGRESS',
                    claimed_at = :claimedAt
                FROM (
                    SELECT notification_job_id
                    FROM notification.notification_job
                    WHERE (
                        status = 'PENDING'
                        AND (scheduled_at IS NULL OR scheduled_at <= CURRENT_TIMESTAMP)
                    ) OR (
                        status = 'IN_PROGRESS'
                        AND COALESCE(claimed_at, ingested_at) < :staleBefore
                    )
                    ORDER BY COALESCE(scheduled_at, ingested_at), notification_job_id
                    LIMIT 20
                    FOR UPDATE SKIP LOCKED
                ) claimed
                WHERE job.notification_job_id = claimed.notification_job_id
                RETURNING job.notification_job_id, job.source_event_id, job.source_service, job.event_type,
                          job.idempotency_key, job.subject, job.body, job.recipient
                """, params(
                "claimedAt", now,
                "staleBefore", staleBefore
        ), (rs, rowNum) -> new PendingNotification(
                rs.getLong("notification_job_id"),
                rs.getString("source_event_id"),
                rs.getString("source_service"),
                rs.getString("event_type"),
                rs.getString("idempotency_key"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getString("recipient")
        )));
    }

    private int nextAttemptNumber(long notificationJobId) {
        return jdbcTemplate.query("""
                SELECT COALESCE(MAX(attempt_number), 0) + 1
                FROM notification.delivery_attempt
                WHERE notification_job_id = :notificationJobId
                """, params("notificationJobId", notificationJobId), rs -> rs.next() ? rs.getInt(1) : 1);
    }

    private void recordSuccess(PendingNotification notification, long webhookEndpointId, int attemptNumber, String responseCode) {
        transactionTemplate.executeWithoutResult(status -> {
            recordAttempt(notification.notificationJobId(), attemptNumber, "SENT", null, responseCode);
            jdbcTemplate.update("""
                    UPDATE notification.notification_job
                    SET status = 'SENT',
                        claimed_at = NULL,
                        scheduled_at = NULL,
                        sent_at = CURRENT_TIMESTAMP,
                        delivered_at = CURRENT_TIMESTAMP
                    WHERE notification_job_id = :notificationJobId
                      AND status = 'IN_PROGRESS'
                    """, params("notificationJobId", notification.notificationJobId()));
            upsertWebhookLog(
                    notification.sourceEventId(),
                    notification.sourceService(),
                    notification.eventType(),
                    notification.idempotencyKey(),
                    webhookEndpointId,
                    "DELIVERED",
                    attemptNumber,
                    notification.body()
            );
        });
    }

    private void recordFailure(
            PendingNotification notification,
            Long webhookEndpointId,
            int attemptNumber,
            String errorSummary,
            boolean terminalFailure
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            recordAttempt(notification.notificationJobId(), attemptNumber, "FAILED", errorSummary, null);
            jdbcTemplate.update("""
                    UPDATE notification.notification_job
                    SET status = :status,
                        claimed_at = NULL,
                        scheduled_at = :scheduledAt,
                        delivered_at = CASE WHEN :terminalFailure THEN CURRENT_TIMESTAMP ELSE delivered_at END
                    WHERE notification_job_id = :notificationJobId
                      AND status = 'IN_PROGRESS'
                    """, params(
                    "status", terminalFailure ? "FAILED" : "PENDING",
                    "scheduledAt", terminalFailure ? null : clock.instant().plusMillis(properties.getRetry().getDelayMs()),
                    "terminalFailure", terminalFailure,
                    "notificationJobId", notification.notificationJobId()
            ));
            if (webhookEndpointId != null) {
                upsertWebhookLog(
                        notification.sourceEventId(),
                        notification.sourceService(),
                        notification.eventType(),
                        notification.idempotencyKey(),
                        webhookEndpointId,
                        terminalFailure ? "FAILED" : "RETRYING",
                        attemptNumber,
                        notification.body()
                );
            }
        });
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
            long webhookEndpointId,
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
                    :webhookEndpointId, :deliveryStatus, :attemptCount, CURRENT_TIMESTAMP,
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
                "webhookEndpointId", webhookEndpointId,
                "deliveryStatus", deliveryStatus,
                "attemptCount", attemptCount,
                "payload", payload
        ));
    }

    private ResolvedWebhookEndpoint resolveWebhookEndpoint(String recipient) {
        String endpointUrl = recipient == null || recipient.isBlank() ? properties.getOpsWebhook().getUrl() : recipient;
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalStateException("No webhook recipient configured");
        }
        ResolvedWebhookEndpoint existing = findWebhookEndpoint(endpointUrl);
        if (existing != null) {
            return existing;
        }
        if (endpointUrl.equals(properties.getOpsWebhook().getUrl())) {
            bootstrapWebhookEndpoint();
            ResolvedWebhookEndpoint bootstrapped = findWebhookEndpoint(endpointUrl);
            if (bootstrapped != null) {
                return bootstrapped;
            }
        }
        throw new IllegalStateException("No active webhook endpoint configured");
    }

    private void validateWebhookConfiguration() {
        String webhookUrl = properties.getOpsWebhook().getUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String webhookSecret = properties.getOpsWebhook().getSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException(
                    "fern.notification.ops-webhook.secret is required when fern.notification.ops-webhook.url is configured"
            );
        }
    }

    private ResolvedWebhookEndpoint findWebhookEndpoint(String endpointUrl) {
        List<ResolvedWebhookEndpoint> endpoints = jdbcTemplate.query("""
                SELECT webhook_endpoint_id, endpoint_url
                FROM notification.webhook_endpoint
                WHERE endpoint_url = :endpointUrl
                  AND status = 'ACTIVE'
                ORDER BY webhook_endpoint_id
                LIMIT 1
                """, params("endpointUrl", endpointUrl), (rs, rowNum) -> new ResolvedWebhookEndpoint(
                rs.getLong("webhook_endpoint_id"),
                rs.getString("endpoint_url"),
                rs.getString("endpoint_url").equals(properties.getOpsWebhook().getUrl())
                        ? properties.getOpsWebhook().getSecret()
                        : ""
        ));
        return endpoints.isEmpty() ? null : endpoints.get(0);
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
            String body,
            String recipient
    ) {
    }

    private record ResolvedWebhookEndpoint(
            long webhookEndpointId,
            String endpointUrl,
            String secret
    ) {
    }
}
