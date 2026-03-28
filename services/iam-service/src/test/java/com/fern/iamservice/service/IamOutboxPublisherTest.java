package com.fern.iamservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.outbox.JdbcOutboxPublisherSupport;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class IamOutboxPublisherTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private IamOutboxPublisher iamOutboxPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        iamOutboxPublisher = new IamOutboxPublisher(jdbcTemplate, kafkaTemplate, clock, 3, Duration.ofMinutes(1));
    }

    @Test
    void shouldMarkEventPublishedAfterKafkaAck() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = pendingEvent(0);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        iamOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("publishedStatus")).isEqualTo("PUBLISHED");
        verify(kafkaTemplate).send(event.eventType(), event.partitionKey(), event.payload());
    }

    @Test
    void shouldKeepEventPendingWhenKafkaSendFails() {
        JdbcOutboxPublisherSupport.ClaimedOutboxEvent event = pendingEvent(0);
        RuntimeException failure = new RuntimeException("kafka unavailable");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) List.of(event));
        when(kafkaTemplate.send(event.eventType(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        iamOutboxPublisher.publishPending();

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo("PENDING");
        assertThat(parameters.getValue().getValue("retryCount")).isEqualTo(1);
    }

    private JdbcOutboxPublisherSupport.ClaimedOutboxEvent pendingEvent(int retryCount) {
        return new JdbcOutboxPublisherSupport.ClaimedOutboxEvent(
                UUID.randomUUID().toString(),
                "role",
                "10",
                "iam.role.changed",
                "10",
                "{\"id\":10}",
                retryCount
        );
    }
}
