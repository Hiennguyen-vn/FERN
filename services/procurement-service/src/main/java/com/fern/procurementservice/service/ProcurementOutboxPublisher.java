package com.fern.procurementservice.service;

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
public class ProcurementOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ProcurementOutboxPublisher.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public ProcurementOutboxPublisher(NamedParameterJdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        List<PendingEvent> events = jdbcTemplate.query("""
                SELECT id, event_type, partition_key, payload::text AS payload
                FROM procurement.outbox_event
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT 20
                """, (rs, rowNum) -> new PendingEvent(
                rs.getString("id"),
                rs.getString("event_type"),
                rs.getString("partition_key"),
                rs.getString("payload")
        ));
        for (PendingEvent event : events) {
            try {
                kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()).join();
                jdbcTemplate.update("""
                        UPDATE procurement.outbox_event
                        SET status = 'PUBLISHED', published_at = :publishedAt
                        WHERE id = CAST(:id AS uuid)
                        """, new MapSqlParameterSource()
                        .addValue("publishedAt", utcNow())
                        .addValue("id", event.id()));
            } catch (RuntimeException exception) {
                log.warn(
                        "procurement_outbox_publish_failed eventId={} eventType={} reason={}",
                        event.id(),
                        event.eventType(),
                        failureReason(exception)
                );
                throw exception;
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

    private record PendingEvent(String id, String eventType, String partitionKey, String payload) {
    }
}
