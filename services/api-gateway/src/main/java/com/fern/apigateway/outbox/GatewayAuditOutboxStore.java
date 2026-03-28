package com.fern.apigateway.outbox;

import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GatewayAuditOutboxStore {
    static final String OUTBOX_TABLE = "gateway.outbox_event";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GatewayAuditOutboxStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<JdbcOutboxPublisherSupport.ClaimedOutboxEvent> claimBatch(
            Instant claimTime,
            Duration reclaimAfter,
            int maxAttempts
    ) {
        return JdbcOutboxPublisherSupport.claimBatch(jdbcTemplate, OUTBOX_TABLE, claimTime, reclaimAfter, maxAttempts);
    }

    public void markPublished(String id, Instant publishedAt) {
        JdbcOutboxPublisherSupport.markPublished(jdbcTemplate, OUTBOX_TABLE, id, publishedAt);
    }

    public JdbcOutboxPublisherSupport.FailureOutcome markFailed(
            JdbcOutboxPublisherSupport.ClaimedOutboxEvent event,
            Instant failedAt,
            int maxAttempts,
            String failureReason
    ) {
        return JdbcOutboxPublisherSupport.markFailed(jdbcTemplate, OUTBOX_TABLE, event, failedAt, maxAttempts, failureReason);
    }
}
