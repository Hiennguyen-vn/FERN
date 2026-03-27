package com.fern.posservice.service;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final Clock clock;

    public PosOutboxPublisher(NamedParameterJdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        jdbcTemplate.query("""
                SELECT id, event_type, partition_key, payload::text AS payload
                FROM pos.outbox_event
                WHERE status = 'PENDING'
                ORDER BY created_at
                LIMIT 20
                """, rs -> {
            while (rs.next()) {
                String id = rs.getString("id");
                String eventType = rs.getString("event_type");
                String partitionKey = rs.getString("partition_key");
                String payload = rs.getString("payload");
                try {
                    kafkaTemplate.send(eventType, partitionKey, payload).join();
                    jdbcTemplate.update("""
                            UPDATE pos.outbox_event
                            SET status = 'PUBLISHED', published_at = :publishedAt
                            WHERE id = CAST(:id AS uuid)
                            """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("publishedAt", clock.instant())
                            .addValue("id", id));
                } catch (RuntimeException exception) {
                    log.warn("pos_outbox_publish_failed eventId={} eventType={} reason={}", id, eventType, exception.toString());
                    throw exception;
                }
            }
        });
    }
}
