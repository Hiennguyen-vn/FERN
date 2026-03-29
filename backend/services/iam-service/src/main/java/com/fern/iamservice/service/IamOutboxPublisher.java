package com.fern.iamservice.service;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class IamOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(IamOutboxPublisher.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration reclaimAfter;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final Counter terminalFailureCounter;

    public IamOutboxPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${fern.outbox.max-attempts:5}") int maxAttempts,
            @Value("${fern.outbox.reclaim-after:PT1M}") Duration reclaimAfter,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.reclaimAfter = reclaimAfter;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.terminalFailureCounter = Counter.builder("fern_outbox_terminal_failures_total")
                .tag("service", "iam-service")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        for (JdbcOutboxPublisherSupport.ClaimedOutboxEvent event : JdbcOutboxPublisherSupport.claimBatch(
                jdbcTemplate,
                "iam.outbox_event",
                clock.instant(),
                reclaimAfter,
                maxAttempts
        )) {
            try {
                kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()).join();
                JdbcOutboxPublisherSupport.markPublished(jdbcTemplate, "iam.outbox_event", event.id(), clock.instant());
            } catch (RuntimeException exception) {
                String failureReason = ExceptionSummaries.safeSummary(exception);
                JdbcOutboxPublisherSupport.FailureOutcome outcome = JdbcOutboxPublisherSupport.markFailed(
                        jdbcTemplate,
                        "iam.outbox_event",
                        event,
                        clock.instant(),
                        maxAttempts,
                        failureReason
                );
                log.warn(
                        "iam_outbox_publish_failed eventId={} eventType={} retryCount={} terminal={} reason={}",
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
                                "IAM outbox publish failed for " + event.eventType(),
                                null,
                                null,
                                null,
                                event.aggregateType(),
                                event.aggregateId(),
                                java.util.Map.of("eventId", event.id(), "eventType", event.eventType(), "errorMessage", outcome.failureReason())
                        );
                    } catch (RuntimeException alertException) {
                        log.error(
                                "iam_outbox_terminal_alert_failed eventId={} eventType={} reason={}",
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
}
