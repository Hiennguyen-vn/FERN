package com.fern.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.notificationservice.config.NotificationProperties;
import com.fern.platform.common.SnowflakeIdGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class NotificationServiceTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private RestClient restClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private NotificationProperties properties;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new NotificationProperties();
        notificationService = new NotificationService(
                jdbcTemplate,
                new SnowflakeIdGenerator(1767225600000L, 11L, 5L),
                new ObjectMapper().findAndRegisterModules(),
                restClient,
                properties,
                Clock.fixed(Instant.parse("2026-03-28T00:00:00Z"), ZoneOffset.UTC),
                transactionTemplate,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldFailStartupWhenWebhookUrlIsConfiguredWithoutSecret() {
        properties.getOpsWebhook().setUrl("https://ops.example.com/webhook");
        properties.getOpsWebhook().setSecret("   ");

        assertThatThrownBy(() -> notificationService.bootstrapWebhookEndpoint())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fern.notification.ops-webhook.secret is required when fern.notification.ops-webhook.url is configured");

        verifyNoInteractions(jdbcTemplate);
    }
}
