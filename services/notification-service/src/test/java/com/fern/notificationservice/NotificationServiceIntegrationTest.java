package com.fern.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.notificationservice.service.NotificationService;
import com.fern.platform.contracts.OperationalAlertEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class NotificationServiceIntegrationTest {
    private static HttpServer webhookServer;
    private static final AtomicInteger webhookStatus = new AtomicInteger(200);
    private static final AtomicInteger webhookRequestCount = new AtomicInteger();
    private static final CopyOnWriteArrayList<String> webhookBodies = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensureWebhookServerStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("notification"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.notification.ops-webhook.url", () -> "http://localhost:" + webhookServer.getAddress().getPort() + "/ops");
        registry.add("fern.notification.ops-webhook.secret", () -> "test-secret");
        registry.add("fern.notification.retry.max-attempts", () -> "2");
        registry.add("fern.notification.retry.delay-ms", () -> "60000");
        registry.add("fern.notification.dlq-topics", () -> "inventory.dlq");
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startWebhookServer() {
        ensureWebhookServerStarted();
    }

    @AfterAll
    static void stopWebhookServer() {
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    notification.delivery_attempt,
                    notification.webhook_delivery_log,
                    notification.notification_job,
                    notification.webhook_endpoint,
                    notification.alert_rule
                RESTART IDENTITY CASCADE
                """);
        notificationService.bootstrapWebhookEndpoint();
        webhookStatus.set(200);
        webhookRequestCount.set(0);
        webhookBodies.clear();
    }

    @Test
    void shouldCreateSingleJobForDuplicateOperationalAlertAndDeliverOnce() throws Exception {
        OperationalAlertEvent event = new OperationalAlertEvent(
                "ops-alert-1",
                "ops.alert.raised",
                Instant.parse("2026-03-27T08:30:00Z"),
                "report-service",
                "corr-export-1",
                "ops-idem-1",
                "EXPORT_FAILED",
                "HIGH",
                "Report export failed for job 42",
                1L,
                101L,
                "EXPORT_JOB",
                "42",
                Map.of("jobId", 42)
        );
        String payload = objectMapper.writeValueAsString(event);

        notificationService.ingestOperationalAlert(payload, event);
        notificationService.ingestOperationalAlert(payload, event);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.notification_job", Integer.class)).isEqualTo(1);

        notificationService.deliverPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification.notification_job WHERE source_event_id = 'ops-alert-1'",
                String.class
        )).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM notification.webhook_delivery_log WHERE source_event_id = 'ops-alert-1'",
                String.class
        )).isEqualTo("DELIVERED");
        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(webhookBodies).singleElement().satisfies(body -> assertThat(body).contains("EXPORT_FAILED"));

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRetryDlqWebhookAndFreezeFailedTerminalState() {
        webhookStatus.set(500);

        notificationService.ingestDlqMessage("inventory.dlq", 1, 42L, "{\"payload\":\"bad-message\"}");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.notification_job", Integer.class)).isEqualTo(1);

        notificationService.deliverPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification.notification_job WHERE source_event_id = 'inventory.dlq:1:42'",
                String.class
        )).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(1);
        jdbcTemplate.update("""
                UPDATE notification.notification_job
                SET scheduled_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE source_event_id = 'inventory.dlq:1:42'
                """);

        notificationService.deliverPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification.notification_job WHERE source_event_id = 'inventory.dlq:1:42'",
                String.class
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM notification.webhook_delivery_log WHERE source_event_id = 'inventory.dlq:1:42'",
                String.class
        )).isEqualTo("FAILED");
        assertThat(webhookRequestCount.get()).isEqualTo(2);
        assertThat(webhookBodies.get(0)).contains("\"topic\":\"inventory.dlq\"");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT subject FROM notification.notification_job WHERE source_event_id = 'inventory.dlq:1:42'",
                String.class
        )).isEqualTo("DLQ message detected on inventory.dlq");

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(2);
    }

    private static void ensureWebhookServerStarted() {
        if (webhookServer != null) {
            return;
        }
        try {
            webhookServer = HttpServer.create(new InetSocketAddress(0), 0);
            webhookServer.createContext("/ops", exchange -> {
                webhookRequestCount.incrementAndGet();
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                webhookBodies.add(new String(requestBody));
                int status = webhookStatus.get();
                byte[] response = (status >= 400 ? "{\"status\":\"failed\"}" : "{\"status\":\"ok\"}").getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, response.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response);
                }
            });
            webhookServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start webhook server", exception);
        }
    }
}
