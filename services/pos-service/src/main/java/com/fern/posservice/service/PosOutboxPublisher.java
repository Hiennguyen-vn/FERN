package com.fern.posservice.service;

import com.fern.posservice.config.PosOutboxProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PosOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(PosOutboxPublisher.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PosOutboxProperties outboxProperties;
    private final Clock clock;

    public PosOutboxPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            PosOutboxProperties outboxProperties,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<PendingEvent> events = jdbcTemplate.query("""
                SELECT id, event_type, partition_key, payload::text AS payload, retry_count
                FROM pos.outbox_event
                WHERE status = :status
                ORDER BY created_at
                LIMIT 20
                """, PosSql.params("status", PosOutboxStatus.PENDING.name()), (rs, rowNum) -> new PendingEvent(
                rs.getString("id"),
                rs.getString("event_type"),
                rs.getString("partition_key"),
                rs.getString("payload"),
                rs.getInt("retry_count")
        ));
        for (PendingEvent event : events) {
            try {
                kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()).join();
                jdbcTemplate.update("""
                        UPDATE pos.outbox_event
                        SET status = :status,
                            published_at = :publishedAt,
                            last_attempt_at = :lastAttemptAt,
                            last_error = NULL
                        WHERE id = CAST(:id AS uuid)
                        """, new MapSqlParameterSource()
                        .addValue("status", PosOutboxStatus.PUBLISHED.name())
                        .addValue("publishedAt", utcNow())
                        .addValue("lastAttemptAt", utcNow())
                        .addValue("id", event.id()));
            } catch (RuntimeException exception) {
                int retryCount = event.retryCount() + 1;
                boolean terminalFailure = retryCount >= outboxProperties.getMaxAttempts();
                log.warn(
                        "pos_outbox_publish_failed eventId={} eventType={} retryCount={} terminal={} reason={}",
                        event.id(),
                        event.eventType(),
                        retryCount,
                        terminalFailure,
                        failureReason(exception)
                );
                jdbcTemplate.update("""
                        UPDATE pos.outbox_event
                        SET status = :status,
                            retry_count = :retryCount,
                            last_attempt_at = :lastAttemptAt,
                            last_error = :lastError
                        WHERE id = CAST(:id AS uuid)
                        """, new MapSqlParameterSource()
                        .addValue("status", terminalFailure ? PosOutboxStatus.FAILED.name() : PosOutboxStatus.PENDING.name())
                        .addValue("retryCount", retryCount)
                        .addValue("lastAttemptAt", utcNow())
                        .addValue("lastError", failureReason(exception))
                        .addValue("id", event.id()));
            }
        }
    }

    private String failureReason(RuntimeException exception) {
        Throwable cause = exception.getCause();
        return cause == null ? exception.toString() : cause.toString();
    }

    private OffsetDateTime utcNow() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record PendingEvent(String id, String eventType, String partitionKey, String payload, int retryCount) {
    }
}
