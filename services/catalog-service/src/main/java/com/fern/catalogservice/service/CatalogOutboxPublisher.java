package com.fern.catalogservice.service;

import com.fern.catalogservice.repository.CatalogOutboxRepository;
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
public class CatalogOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(CatalogOutboxPublisher.class);

    private final CatalogOutboxRepository catalogOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public CatalogOutboxPublisher(
            CatalogOutboxRepository catalogOutboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock
    ) {
        this.catalogOutboxRepository = catalogOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fern.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPending() {
        for (var event : catalogOutboxRepository.findTop20ByStatusOrderByCreatedAtAsc("PENDING")) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload()).join();
            } catch (RuntimeException exception) {
                log.warn(
                        "catalog_outbox_publish_failed eventId={} eventType={} reason={}",
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
