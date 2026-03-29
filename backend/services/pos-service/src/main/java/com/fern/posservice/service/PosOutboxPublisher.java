package com.fern.posservice.service;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import com.fern.posservice.config.PosOutboxProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PosOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(PosOutboxPublisher.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PosOutboxProperties outboxProperties;
    private final Clock clock;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final Counter terminalFailureCounter;

    public PosOutboxPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            PosOutboxProperties outboxProperties,
            Clock clock,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.terminalFailureCounter = Counter.builder("fern_outbox_terminal_failures_total")
                .tag("service", "pos-service")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        List<JdbcOutboxPublisherSupport.ClaimedOutboxEvent> events = JdbcOutboxPublisherSupport.claimBatch(
                jdbcTemplate,
                "pos.outbox_event",
                clock.instant(),
                outboxProperties.getReclaimAfter(),
                outboxProperties.getMaxAttempts()
        );
        for (JdbcOutboxPublisherSupport.ClaimedOutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()).join();
                JdbcOutboxPublisherSupport.markPublished(jdbcTemplate, "pos.outbox_event", event.id(), clock.instant());
            } catch (RuntimeException exception) {
                String failureReason = ExceptionSummaries.safeSummary(exception);
                JdbcOutboxPublisherSupport.FailureOutcome outcome = JdbcOutboxPublisherSupport.markFailed(
                        jdbcTemplate,
                        "pos.outbox_event",
                        event,
                        clock.instant(),
                        outboxProperties.getMaxAttempts(),
                        failureReason
                );
                log.warn(
                        "pos_outbox_publish_failed eventId={} eventType={} retryCount={} terminal={} reason={}",
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
                                "POS outbox publish failed for " + event.eventType(),
                                null,
                                null,
                                null,
                                event.aggregateType(),
                                event.aggregateId(),
                                java.util.Map.of("eventId", event.id(), "eventType", event.eventType(), "errorMessage", outcome.failureReason())
                        );
                    } catch (RuntimeException alertException) {
                        log.error(
                                "pos_outbox_terminal_alert_failed eventId={} eventType={} reason={}",
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
