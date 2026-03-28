package com.fern.hrservice.service;

import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class HrOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(HrOutboxPublisher.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration reclaimAfter;

    public HrOutboxPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${fern.outbox.max-attempts:5}") int maxAttempts,
            @Value("${fern.outbox.reclaim-after:PT1M}") Duration reclaimAfter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.reclaimAfter = reclaimAfter;
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        List<JdbcOutboxPublisherSupport.ClaimedOutboxEvent> events = JdbcOutboxPublisherSupport.claimBatch(
                jdbcTemplate,
                "hr.outbox_event",
                clock.instant(),
                reclaimAfter,
                maxAttempts
        );
        for (JdbcOutboxPublisherSupport.ClaimedOutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()).join();
                JdbcOutboxPublisherSupport.markPublished(jdbcTemplate, "hr.outbox_event", event.id(), clock.instant());
            } catch (RuntimeException exception) {
                String failureReason = ExceptionSummaries.safeSummary(exception);
                JdbcOutboxPublisherSupport.FailureOutcome outcome = JdbcOutboxPublisherSupport.markFailed(
                        jdbcTemplate,
                        "hr.outbox_event",
                        event,
                        clock.instant(),
                        maxAttempts,
                        failureReason
                );
                log.warn(
                        "hr_outbox_publish_failed eventId={} eventType={} retryCount={} terminal={} reason={}",
                        event.id(),
                        event.eventType(),
                        outcome.retryCount(),
                        outcome.terminalFailure(),
                        outcome.failureReason(),
                        exception
                );
            }
        }
    }
}
