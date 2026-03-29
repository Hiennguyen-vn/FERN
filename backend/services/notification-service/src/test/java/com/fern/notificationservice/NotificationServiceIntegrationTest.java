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
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private static final AtomicInteger webhookDelayMs = new AtomicInteger();
    private static final AtomicInteger webhookRequestCount = new AtomicInteger();
    private static final Queue<Integer> webhookStatusSequence = new ConcurrentLinkedQueue<>();
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
        webhookDelayMs.set(0);
        webhookRequestCount.set(0);
        webhookStatusSequence.clear();
        webhookBodies.clear();
    }

    @Test
    void shouldCreateSingleJobForDuplicateOperationalAlertAndDeliverOnce() throws Exception {
        Long webhookEndpointId = jdbcTemplate.queryForObject(
                "SELECT webhook_endpoint_id FROM notification.webhook_endpoint WHERE endpoint_url = ?",
                Long.class,
                "http://localhost:" + webhookServer.getAddress().getPort() + "/ops"
        );
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT webhook_endpoint_id FROM notification.webhook_delivery_log WHERE source_event_id = 'ops-alert-1'",
                Long.class
        )).isEqualTo(webhookEndpointId);
        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(webhookBodies).singleElement().satisfies(body -> assertThat(body).contains("EXPORT_FAILED"));

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRetryDlqWebhookAndFreezeFailedTerminalState() {
        Long webhookEndpointId = jdbcTemplate.queryForObject(
                "SELECT webhook_endpoint_id FROM notification.webhook_endpoint WHERE endpoint_url = ?",
                Long.class,
                "http://localhost:" + webhookServer.getAddress().getPort() + "/ops"
        );
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT webhook_endpoint_id FROM notification.webhook_delivery_log WHERE source_event_id = 'inventory.dlq:1:42'",
                Long.class
        )).isEqualTo(webhookEndpointId);
        assertThat(jdbcTemplate.queryForList(
                "SELECT error_message FROM notification.delivery_attempt ORDER BY attempt_number",
                String.class
        )).allSatisfy(errorMessage -> assertThat(errorMessage)
                .matches("^[A-Za-z0-9$.]+( -> [A-Za-z0-9$.]+){0,3}$")
                .doesNotContain("localhost")
                .doesNotContain("failed"));
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

    @Test
    void shouldReplayFailedOperationalAlertWhenEventIsRedelivered() throws Exception {
        webhookStatus.set(500);
        OperationalAlertEvent event = new OperationalAlertEvent(
                "ops-alert-replay-failed",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:40:00Z"),
                "report-service",
                "corr-replay-failed",
                "ops-idem-replay-failed",
                "EXPORT_FAILED",
                "HIGH",
                "Replay failed alert",
                1L,
                101L,
                "EXPORT_JOB",
                "301",
                Map.of("jobId", 301)
        );
        String payload = objectMapper.writeValueAsString(event);

        notificationService.ingestOperationalAlert(payload, event);
        notificationService.deliverPending();
        jdbcTemplate.update("""
                UPDATE notification.notification_job
                SET scheduled_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE source_event_id = 'ops-alert-replay-failed'
                """);
        notificationService.deliverPending();

        assertThat(jobStatus("ops-alert-replay-failed")).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(2);

        webhookStatus.set(200);
        notificationService.ingestOperationalAlert(payload, event);

        assertThat(jobStatus("ops-alert-replay-failed")).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.notification_job", Integer.class)).isEqualTo(1);

        notificationService.deliverPending();

        assertThat(jobStatus("ops-alert-replay-failed")).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(3);
        assertThat(webhookRequestCount.get()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT delivery_status
                FROM notification.webhook_delivery_log
                WHERE source_event_id = 'ops-alert-replay-failed'
                """, String.class)).isEqualTo("DELIVERED");
    }

    @Test
    void shouldReplayFailedDlqNotificationWhenMessageIsRedelivered() {
        webhookStatus.set(500);

        notificationService.ingestDlqMessage("inventory.dlq", 3, 77L, "{\"payload\":\"replay-me\"}");
        notificationService.deliverPending();
        jdbcTemplate.update("""
                UPDATE notification.notification_job
                SET scheduled_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE source_event_id = 'inventory.dlq:3:77'
                """);
        notificationService.deliverPending();

        assertThat(jobStatus("inventory.dlq:3:77")).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(2);

        webhookStatus.set(200);
        notificationService.ingestDlqMessage("inventory.dlq", 3, 77L, "{\"payload\":\"replay-me\"}");

        assertThat(jobStatus("inventory.dlq:3:77")).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.notification_job", Integer.class)).isEqualTo(1);

        notificationService.deliverPending();

        assertThat(jobStatus("inventory.dlq:3:77")).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(3);
        assertThat(webhookRequestCount.get()).isEqualTo(3);
    }

    @Test
    void shouldTreatOperationalAlertReplayWithNewEventIdButSameIdempotencyKeyAsSameJob() throws Exception {
        OperationalAlertEvent first = new OperationalAlertEvent(
                "ops-alert-idem-source-1",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:45:00Z"),
                "report-service",
                "corr-idem-source-1",
                "ops-idem-source-shared",
                "EXPORT_FAILED",
                "HIGH",
                "Idempotent alert replay",
                1L,
                101L,
                "EXPORT_JOB",
                "401",
                Map.of("jobId", 401)
        );
        OperationalAlertEvent replay = new OperationalAlertEvent(
                "ops-alert-idem-source-2",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:45:05Z"),
                "report-service",
                "corr-idem-source-2",
                "ops-idem-source-shared",
                "EXPORT_FAILED",
                "HIGH",
                "Idempotent alert replay",
                1L,
                101L,
                "EXPORT_JOB",
                "401",
                Map.of("jobId", 401)
        );

        notificationService.ingestOperationalAlert(objectMapper.writeValueAsString(first), first);
        notificationService.ingestOperationalAlert(objectMapper.writeValueAsString(replay), replay);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.notification_job", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification.notification_job
                WHERE idempotency_key = 'ops-idem-source-shared'
                """, Integer.class)).isEqualTo(1);

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification.webhook_delivery_log
                WHERE idempotency_key = 'ops-idem-source-shared'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldReplayFailedOperationalAlertWhenEventIdChangesButIdempotencyKeyMatches() throws Exception {
        webhookStatus.set(500);
        OperationalAlertEvent first = new OperationalAlertEvent(
                "ops-alert-idem-failed-1",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:50:00Z"),
                "report-service",
                "corr-idem-failed-1",
                "ops-idem-failed-shared",
                "EXPORT_FAILED",
                "HIGH",
                "Replay after failure",
                1L,
                101L,
                "EXPORT_JOB",
                "402",
                Map.of("jobId", 402)
        );
        OperationalAlertEvent replay = new OperationalAlertEvent(
                "ops-alert-idem-failed-2",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:50:05Z"),
                "report-service",
                "corr-idem-failed-2",
                "ops-idem-failed-shared",
                "EXPORT_FAILED",
                "HIGH",
                "Replay after failure",
                1L,
                101L,
                "EXPORT_JOB",
                "402",
                Map.of("jobId", 402)
        );

        notificationService.ingestOperationalAlert(objectMapper.writeValueAsString(first), first);
        notificationService.deliverPending();
        jdbcTemplate.update("""
                UPDATE notification.notification_job
                SET scheduled_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE idempotency_key = 'ops-idem-failed-shared'
                """);
        notificationService.deliverPending();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE idempotency_key = 'ops-idem-failed-shared'
                """, String.class)).isEqualTo("FAILED");

        webhookStatus.set(200);
        notificationService.ingestOperationalAlert(objectMapper.writeValueAsString(replay), replay);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification.notification_job
                WHERE idempotency_key = 'ops-idem-failed-shared'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE idempotency_key = 'ops-idem-failed-shared'
                """, String.class)).isEqualTo("PENDING");

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE idempotency_key = 'ops-idem-failed-shared'
                """, String.class)).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification.webhook_delivery_log
                WHERE idempotency_key = 'ops-idem-failed-shared'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldClaimPendingNotificationOnceAcrossConcurrentSchedulers() throws Exception {
        webhookDelayMs.set(200);
        OperationalAlertEvent event = new OperationalAlertEvent(
                "ops-alert-concurrent",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:00:00Z"),
                "report-service",
                "corr-concurrent",
                "ops-idem-concurrent",
                "EXPORT_FAILED",
                "HIGH",
                "Concurrent delivery test",
                1L,
                101L,
                "EXPORT_JOB",
                "99",
                Map.of("jobId", 99)
        );
        String payload = objectMapper.writeValueAsString(event);
        notificationService.ingestOperationalAlert(payload, event);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                notificationService.deliverPending();
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                notificationService.deliverPending();
            });
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }

        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification.notification_job WHERE source_event_id = 'ops-alert-concurrent'",
                String.class
        )).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification.delivery_attempt WHERE notification_job_id = (SELECT notification_job_id FROM notification.notification_job WHERE source_event_id = 'ops-alert-concurrent')",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void shouldReclaimStaleInProgressNotificationAfterCrashAndDeliverOnce() throws Exception {
        OperationalAlertEvent event = new OperationalAlertEvent(
                "ops-alert-stale-claim",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:20:00Z"),
                "report-service",
                "corr-stale-claim",
                "ops-idem-stale-claim",
                "EXPORT_FAILED",
                "HIGH",
                "Stale claim recovery test",
                1L,
                101L,
                "EXPORT_JOB",
                "101",
                Map.of("jobId", 101)
        );
        String payload = objectMapper.writeValueAsString(event);
        notificationService.ingestOperationalAlert(payload, event);
        jdbcTemplate.update("""
                UPDATE notification.notification_job
                SET status = 'IN_PROGRESS',
                    claimed_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                WHERE source_event_id = 'ops-alert-stale-claim'
                """);

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE source_event_id = 'ops-alert-stale-claim'
                """, String.class)).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notification.delivery_attempt
                WHERE notification_job_id = (
                    SELECT notification_job_id
                    FROM notification.notification_job
                    WHERE source_event_id = 'ops-alert-stale-claim'
                )
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldContinueDeliveringRemainingBatchWhenFirstNotificationFails() throws Exception {
        webhookStatusSequence.add(500);
        webhookStatusSequence.add(200);
        OperationalAlertEvent first = new OperationalAlertEvent(
                "ops-alert-batch-fail-1",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:25:00Z"),
                "report-service",
                "corr-batch-fail-1",
                "ops-idem-batch-fail-1",
                "EXPORT_FAILED",
                "HIGH",
                "First alert should retry",
                1L,
                101L,
                "EXPORT_JOB",
                "201",
                Map.of("jobId", 201)
        );
        OperationalAlertEvent second = new OperationalAlertEvent(
                "ops-alert-batch-fail-2",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:26:00Z"),
                "report-service",
                "corr-batch-fail-2",
                "ops-idem-batch-fail-2",
                "EXPORT_FAILED",
                "HIGH",
                "Second alert should still send",
                1L,
                101L,
                "EXPORT_JOB",
                "202",
                Map.of("jobId", 202)
        );
        notificationService.ingestOperationalAlert(objectMapper.writeValueAsString(first), first);
        notificationService.ingestOperationalAlert(objectMapper.writeValueAsString(second), second);

        notificationService.deliverPending();

        assertThat(webhookRequestCount.get()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE source_event_id = 'ops-alert-batch-fail-1'
                """, String.class)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE source_event_id = 'ops-alert-batch-fail-2'
                """, String.class)).isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification.delivery_attempt", Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldResolveWebhookEndpointIdForDeliveryLogs() throws Exception {
        OperationalAlertEvent event = new OperationalAlertEvent(
                "ops-alert-endpoint-id",
                "ops.alert.raised",
                Instant.parse("2026-03-27T09:15:00Z"),
                "report-service",
                "corr-endpoint-id",
                "ops-idem-endpoint-id",
                "EXPORT_FAILED",
                "HIGH",
                "Endpoint id resolution test",
                1L,
                101L,
                "EXPORT_JOB",
                "100",
                Map.of("jobId", 100)
        );
        String payload = objectMapper.writeValueAsString(event);

        notificationService.ingestOperationalAlert(payload, event);
        notificationService.deliverPending();

        Long expectedWebhookEndpointId = jdbcTemplate.queryForObject(
                "SELECT webhook_endpoint_id FROM notification.webhook_endpoint WHERE endpoint_url = ?",
                Long.class,
                "http://localhost:" + webhookServer.getAddress().getPort() + "/ops"
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT webhook_endpoint_id FROM notification.webhook_delivery_log WHERE source_event_id = 'ops-alert-endpoint-id'",
                Long.class
        )).isEqualTo(expectedWebhookEndpointId);
    }

    private String jobStatus(String sourceEventId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification.notification_job
                WHERE source_event_id = ?
                """, String.class, sourceEventId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", exception);
        }
    }

    private static void ensureWebhookServerStarted() {
        if (webhookServer != null) {
            return;
        }
        try {
            webhookServer = HttpServer.create(new InetSocketAddress(0), 0);
            webhookServer.createContext("/ops", exchange -> {
                int delay = webhookDelayMs.get();
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                webhookRequestCount.incrementAndGet();
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                webhookBodies.add(new String(requestBody));
                Integer sequencedStatus = webhookStatusSequence.poll();
                int status = sequencedStatus == null ? webhookStatus.get() : sequencedStatus;
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
