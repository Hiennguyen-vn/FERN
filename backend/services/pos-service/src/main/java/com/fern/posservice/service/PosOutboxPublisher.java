package com.fern.posservice.service;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.outbox.AbstractJdbcOutboxPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PosOutboxPublisher extends AbstractJdbcOutboxPublisher {

    private final Duration retentionPeriod;

    public PosOutboxPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock,
            @Value("${fern.outbox.max-attempts:5}") int maxAttempts,
            @Value("${fern.outbox.reclaim-after:PT1M}") Duration reclaimAfter,
            @Value("${fern.outbox.retention-days:30}") int retentionDays,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry
    ) {
        super(
                jdbcTemplate,
                kafkaTemplate,
                clock,
                maxAttempts,
                reclaimAfter,
                operationalAlertPublisher,
                meterRegistry,
                "pos-service",
                "pos",
                "POS");
        this.retentionPeriod = Duration.ofDays(retentionDays);
    }

    @Override
    protected String qualifiedOutboxTable() {
        return "pos.outbox_event";
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        publishPendingBatch();
    }

    /**
     * Purge old outbox events that have already been successfully sent.
     * Runs daily at 3:00 AM server time.
     */
    @Scheduled(cron = "${fern.outbox.purge-cron:0 0 3 * * *}")
    public void purgeOldEvents() {
        purgeSentEvents(retentionPeriod);
    }
}
