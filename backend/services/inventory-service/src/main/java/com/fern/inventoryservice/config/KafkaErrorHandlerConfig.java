package com.fern.inventoryservice.config;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configures Kafka consumer error handling with Dead Letter Queue (DLQ) support.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Retry failed messages up to 3 times with 1-second intervals</li>
 *   <li>After exhausting retries, publish to {@code <original-topic>.DLQ}</li>
 *   <li>Non-retryable exceptions (deserialization, validation) skip retries and go directly to DLQ</li>
 * </ul>
 *
 * <p>This prevents poisoned messages from blocking the consumer group while ensuring
 * failed events are preserved for investigation and replay.
 */
@Configuration
public class KafkaErrorHandlerConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRIES = 3L;

    /**
     * DLQ topic naming: {@code <original-topic>.DLQ}
     * e.g. {@code pos.sale.completed.DLQ}
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<String, String> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    log.error(
                            "Sending message to DLQ after {} retries — topic={}, partition={}, offset={}: {}",
                            MAX_RETRIES, record.topic(), record.partition(), record.offset(), ex.getMessage()
                    );
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLQ", record.partition());
                }
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES)
        );

        // Non-retryable: deserialization and validation errors will never succeed on retry
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }
}
