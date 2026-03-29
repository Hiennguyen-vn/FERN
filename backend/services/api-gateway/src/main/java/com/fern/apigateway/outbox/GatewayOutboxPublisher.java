package com.fern.apigateway.outbox;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(GatewayOutboxPublisher.class);

    private final GatewayAuditOutboxStore gatewayAuditOutboxStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration reclaimAfter;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final Counter terminalFailureCounter;

    public GatewayOutboxPublisher(
            GatewayAuditOutboxStore gatewayAuditOutboxStore,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${fern.outbox.max-attempts:5}") int maxAttempts,
            @Value("${fern.outbox.reclaim-after:PT1M}") Duration reclaimAfter,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry
    ) {
        this.gatewayAuditOutboxStore = gatewayAuditOutboxStore;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.reclaimAfter = reclaimAfter;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.terminalFailureCounter = Counter.builder("fern_outbox_terminal_failures_total")
                .tag("service", "api-gateway")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        for (JdbcOutboxPublisherSupport.ClaimedOutboxEvent event : gatewayAuditOutboxStore.claimBatch(
                clock.instant(),
                reclaimAfter,
                maxAttempts
        )) {
            try {
                kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()).join();
                gatewayAuditOutboxStore.markPublished(event.id(), clock.instant());
            } catch (RuntimeException exception) {
                String failureReason = ExceptionSummaries.safeSummary(exception);
                JdbcOutboxPublisherSupport.FailureOutcome outcome = gatewayAuditOutboxStore.markFailed(
                        event,
                        clock.instant(),
                        maxAttempts,
                        failureReason
                );
                log.warn(
                        "gateway_outbox_publish_failed eventId={} eventType={} retryCount={} terminal={} reason={}",
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
                                "Gateway outbox publish failed for " + event.eventType(),
                                null,
                                null,
                                null,
                                event.aggregateType(),
                                event.aggregateId(),
                                java.util.Map.of("eventId", event.id(), "eventType", event.eventType(), "errorMessage", outcome.failureReason())
                        );
                    } catch (RuntimeException alertException) {
                        log.error(
                                "gateway_outbox_terminal_alert_failed eventId={} eventType={} reason={}",
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
