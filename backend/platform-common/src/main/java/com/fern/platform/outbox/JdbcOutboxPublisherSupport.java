package com.fern.platform.outbox;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class JdbcOutboxPublisherSupport {
    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final java.util.regex.Pattern CORRELATION_ID_PATTERN = java.util.regex.Pattern.compile("\"correlationId\"\\s*:\\s*\"([^\"]+)\"");

    private JdbcOutboxPublisherSupport() {
    }

    public static List<ClaimedOutboxEvent> claimBatch(
            NamedParameterJdbcTemplate jdbcTemplate,
            String qualifiedTable,
            Instant claimTime,
            Duration reclaimAfter,
            int maxAttempts
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("pendingStatus", "PENDING")
                .addValue("inProgressStatus", "IN_PROGRESS")
                .addValue("claimTime", toUtc(claimTime))
                .addValue("staleBefore", toUtc(claimTime.minus(reclaimAfter)))
                .addValue("maxAttempts", maxAttempts)
                .addValue("batchSize", DEFAULT_BATCH_SIZE);
        return jdbcTemplate.query("""
                UPDATE %1$s outbox
                SET status = :inProgressStatus,
                    last_attempt_at = :claimTime
                FROM (
                    SELECT id
                    FROM %1$s
                    WHERE retry_count < :maxAttempts
                      AND (
                        status = :pendingStatus
                        OR (
                            status = :inProgressStatus
                            AND COALESCE(last_attempt_at, created_at) < :staleBefore
                        )
                      )
                    ORDER BY created_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                ) claimed
                WHERE outbox.id = claimed.id
                RETURNING outbox.id::text AS id,
                          outbox.aggregate_type,
                          outbox.aggregate_id,
                          outbox.event_type,
                          outbox.partition_key,
                          outbox.payload::text AS payload,
                          outbox.retry_count
                """.formatted(qualifiedTable), parameters, (rs, rowNum) -> new ClaimedOutboxEvent(
                rs.getString("id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("partition_key"),
                rs.getString("payload"),
                rs.getInt("retry_count")
        ));
    }

    public static void markPublished(
            NamedParameterJdbcTemplate jdbcTemplate,
            String qualifiedTable,
            String id,
            Instant publishedAt
    ) {
        jdbcTemplate.update("""
                UPDATE %s
                SET status = :publishedStatus,
                    published_at = :publishedAt,
                    last_attempt_at = :publishedAt,
                    last_error = NULL
                WHERE id = CAST(:id AS uuid)
                """.formatted(qualifiedTable), new MapSqlParameterSource()
                .addValue("publishedStatus", "PUBLISHED")
                .addValue("publishedAt", toUtc(publishedAt))
                .addValue("id", id));
    }

    public static FailureOutcome markFailed(
            NamedParameterJdbcTemplate jdbcTemplate,
            String qualifiedTable,
            ClaimedOutboxEvent event,
            Instant failedAt,
            int maxAttempts,
            String failureReason
    ) {
        int retryCount = event.retryCount() + 1;
        boolean terminalFailure = retryCount >= maxAttempts;
        jdbcTemplate.update("""
                UPDATE %s
                SET status = :status,
                    retry_count = :retryCount,
                    last_attempt_at = :lastAttemptAt,
                    last_error = :lastError
                WHERE id = CAST(:id AS uuid)
                """.formatted(qualifiedTable), new MapSqlParameterSource()
                .addValue("status", terminalFailure ? "FAILED" : "PENDING")
                .addValue("retryCount", retryCount)
                .addValue("lastAttemptAt", toUtc(failedAt))
                .addValue("lastError", failureReason)
                .addValue("id", event.id()));
        return new FailureOutcome(terminalFailure, retryCount, failureReason);
    }

    private static OffsetDateTime toUtc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record ClaimedOutboxEvent(
            String id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String partitionKey,
            String payload,
            int retryCount
    ) {
    }

    public record FailureOutcome(boolean terminalFailure, int retryCount, String failureReason) {
    }

    public static String extractCorrelationId(String payload) {
        if (payload == null) {
            return null;
        }
        java.util.regex.Matcher matcher = CORRELATION_ID_PATTERN.matcher(payload);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
