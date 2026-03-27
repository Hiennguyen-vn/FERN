package com.fern.orgservice.service;

import com.fern.orgservice.repository.OrgOutboxRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "fern.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OrgOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrgOutboxPublisher.class);

    private final OrgOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OrgOutboxPublisher(OrgOutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        for (var event : outboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload()).join();
            } catch (RuntimeException exception) {
                log.warn(
                        "org_outbox_publish_failed eventId={} eventType={} reason={}",
                        event.getId(),
                        event.getEventType(),
                        failureReason(exception)
                );
                throw exception;
            }
            event.setStatus("PUBLISHED");
            event.setPublishedAt(clock.instant());
        }
    }

    private String failureReason(RuntimeException exception) {
        Throwable cause = exception.getCause();
        return cause == null ? exception.toString() : cause.toString();
    }
}
