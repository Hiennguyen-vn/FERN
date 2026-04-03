package com.fern.platform.outbox;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Shared batch publish loop for JDBC-backed outbox tables (schema-qualified name).
 */
public abstract class AbstractJdbcOutboxPublisher {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration reclaimAfter;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final Counter terminalFailureCounter;
    private final String logPrefix;
    private final String humanServiceLabel;

    protected AbstractJdbcOutboxPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            int maxAttempts,
            Duration reclaimAfter,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry,
            String serviceMetricTag,
            String logPrefix,
            String humanServiceLabel
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.reclaimAfter = reclaimAfter;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.logPrefix = logPrefix;
        this.humanServiceLabel = humanServiceLabel;
        this.terminalFailureCounter = Counter.builder("fern_outbox_terminal_failures_total")
                .tag("service", serviceMetricTag)
                .register(meterRegistry);
    }

    protected abstract String qualifiedOutboxTable();

    protected final void publishPendingBatch() {
        List<JdbcOutboxPublisherSupport.ClaimedOutboxEvent> events = JdbcOutboxPublisherSupport.claimBatch(
                jdbcTemplate,
                qualifiedOutboxTable(),
                clock.instant(),
                reclaimAfter,
                maxAttempts
        );
        String table = qualifiedOutboxTable();
        for (JdbcOutboxPublisherSupport.ClaimedOutboxEvent event : events) {
            try {
                String correlationId = JdbcOutboxPublisherSupport.extractCorrelationId(event.payload());
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        event.eventType(),
                        event.partitionKey(),
                        event.payload()
                );
                if (correlationId != null) {
                    record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
                }
                kafkaTemplate.send(record).join();
                JdbcOutboxPublisherSupport.markPublished(jdbcTemplate, table, event.id(), clock.instant());
            } catch (RuntimeException exception) {
                String failureReason = ExceptionSummaries.safeSummary(exception);
                JdbcOutboxPublisherSupport.FailureOutcome outcome = JdbcOutboxPublisherSupport.markFailed(
                        jdbcTemplate,
                        table,
                        event,
                        clock.instant(),
                        maxAttempts,
                        failureReason
                );
                log.warn(
                        "{}_outbox_publish_failed eventId={} eventType={} retryCount={} terminal={} reason={}",
                        logPrefix,
                        event.id(),
                        event.eventType(),
                        outcome.retryCount(),
                        outcome.terminalFailure(),
                        outcome.failureReason(),
                        exception
                );
                if (outcome.terminalFailure()) {
                    terminalFailureCounter.increment();
                    try {
                        operationalAlertPublisher.publish(
                                "OUTBOX_TERMINAL_FAILURE",
                                "HIGH",
                                humanServiceLabel + " outbox publish failed for " + event.eventType(),
                                null,
                                null,
                                null,
                                event.aggregateType(),
                                event.aggregateId(),
                                Map.of(
                                        "eventId", event.id(),
                                        "eventType", event.eventType(),
                                        "errorMessage", outcome.failureReason()
                                )
                        );
                    } catch (RuntimeException alertException) {
                        log.error(
                                "{}_outbox_terminal_alert_failed eventId={} eventType={} reason={}",
                                logPrefix,
                                event.id(),
                                event.eventType(),
                                ExceptionSummaries.safeSummary(alertException),
                                alertException
                        );
                    }
                }
            }
        }
    }

    /**
     * Purge outbox events that have been successfully sent and are older than the given retention period.
     * Subclasses should call this from a {@code @Scheduled} method (e.g., daily at 3 AM).
     *
     * @param retention how long to keep sent events before purging
     * @return the number of purged events
     */
    protected final int purgeSentEvents(Duration retention) {
        java.time.Instant cutoff = clock.instant().minus(retention);
        int deleted = jdbcTemplate.update(
                "DELETE FROM " + qualifiedOutboxTable() + " WHERE status = 'SENT' AND created_at < :cutoff",
                Map.of("cutoff", java.time.OffsetDateTime.ofInstant(cutoff, java.time.ZoneOffset.UTC))
        );
        if (deleted > 0) {
            log.info("{}_outbox_purge deleted={} olderThan={}", logPrefix, deleted, cutoff);
        }
        return deleted;
    }
}
