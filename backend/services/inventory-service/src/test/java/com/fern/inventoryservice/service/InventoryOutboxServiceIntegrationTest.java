package com.fern.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class InventoryOutboxServiceIntegrationTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("inventory"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.clients.org.base-url", () -> "http://localhost");
        registry.add("fern.security.jwt.secret", () -> "inventory-outbox-service-secret-012345678901234567890123");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private InventoryOutboxService inventoryOutboxService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE inventory.outbox_event
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldDeduplicateInventoryAdjustmentOutboxForSameAggregate() {
        inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8801", "inventory.adjustment.posted", "101", adjustmentEvent(
                8801L,
                "event-adjustment-1",
                "corr-adjustment-1",
                "inventory.adjustment.posted:adjustment:8801",
                new BigDecimal("5.0000")
        ));
        inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8801", "inventory.adjustment.posted", "101", adjustmentEvent(
                8801L,
                "event-adjustment-2",
                "corr-adjustment-2",
                "inventory.adjustment.posted:adjustment:8801",
                new BigDecimal("5.0000")
        ));

        assertThat(count("""
                SELECT COUNT(*)
                FROM inventory.outbox_event
                WHERE aggregate_type = 'STOCK_ADJUSTMENT'
                  AND aggregate_id = '8801'
                  AND event_type = 'inventory.adjustment.posted'
                """)).isEqualTo(1);
    }

    @Test
    void shouldRejectInventoryAdjustmentOutboxConflictWhenPayloadChanges() {
        inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8802", "inventory.adjustment.posted", "101", adjustmentEvent(
                8802L,
                "event-adjustment-conflict-1",
                "corr-adjustment-conflict-1",
                "inventory.adjustment.posted:adjustment:8802",
                new BigDecimal("5.0000")
        ));

        assertThatThrownBy(() -> inventoryOutboxService.enqueueOutbox(
                "STOCK_ADJUSTMENT",
                "8802",
                "inventory.adjustment.posted",
                "101",
                adjustmentEvent(
                        8802L,
                        "event-adjustment-conflict-2",
                        "corr-adjustment-conflict-2",
                        "inventory.adjustment.posted:adjustment:8802",
                        new BigDecimal("6.0000")
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory outbox idempotency conflict");

        assertThat(count("""
                SELECT COUNT(*)
                FROM inventory.outbox_event
                WHERE aggregate_type = 'STOCK_ADJUSTMENT'
                  AND aggregate_id = '8802'
                  AND event_type = 'inventory.adjustment.posted'
                """)).isEqualTo(1);
    }

    @Test
    void shouldDeduplicateConcurrentEquivalentInventoryAdjustmentOutboxRequests() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8803", "inventory.adjustment.posted", "101", adjustmentEvent(
                        8803L,
                        "event-adjustment-concurrent-1",
                        "corr-adjustment-concurrent-1",
                        "inventory.adjustment.posted:adjustment:8803",
                        new BigDecimal("5.0000")
                ));
                return null;
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8803", "inventory.adjustment.posted", "101", adjustmentEvent(
                        8803L,
                        "event-adjustment-concurrent-2",
                        "corr-adjustment-concurrent-2",
                        "inventory.adjustment.posted:adjustment:8803",
                        new BigDecimal("5.0000")
                ));
                return null;
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(count("""
                SELECT COUNT(*)
                FROM inventory.outbox_event
                WHERE aggregate_type = 'STOCK_ADJUSTMENT'
                  AND aggregate_id = '8803'
                  AND event_type = 'inventory.adjustment.posted'
                """)).isEqualTo(1);
    }

    @Test
    void shouldRejectInventoryAdjustmentOutboxConflictWhenPartitionKeyChanges() {
        inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8804", "inventory.adjustment.posted", "101", adjustmentEvent(
                8804L,
                "event-adjustment-partition-1",
                "corr-adjustment-partition-1",
                "inventory.adjustment.posted:adjustment:8804",
                new BigDecimal("5.0000")
        ));

        assertThatThrownBy(() -> inventoryOutboxService.enqueueOutbox(
                "STOCK_ADJUSTMENT",
                "8804",
                "inventory.adjustment.posted",
                "102",
                adjustmentEvent(
                        8804L,
                        "event-adjustment-partition-2",
                        "corr-adjustment-partition-2",
                        "inventory.adjustment.posted:adjustment:8804",
                        new BigDecimal("5.0000")
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory outbox idempotency conflict");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT partition_key
                FROM inventory.outbox_event
                WHERE aggregate_type = 'STOCK_ADJUSTMENT'
                  AND aggregate_id = '8804'
                  AND event_type = 'inventory.adjustment.posted'
                """, String.class)).isEqualTo("101");
    }

    @Test
    void shouldReviveFailedInventoryAdjustmentOutboxWhenEquivalentReplayArrives() {
        inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8805", "inventory.adjustment.posted", "101", adjustmentEvent(
                8805L,
                "event-adjustment-failed-1",
                "corr-adjustment-failed-1",
                "inventory.adjustment.posted:adjustment:8805",
                new BigDecimal("5.0000")
        ));
        UUID eventId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM inventory.outbox_event
                WHERE aggregate_type = 'STOCK_ADJUSTMENT'
                  AND aggregate_id = '8805'
                  AND event_type = 'inventory.adjustment.posted'
                """, UUID.class);
        jdbcTemplate.update("""
                UPDATE inventory.outbox_event
                SET status = 'FAILED',
                    retry_count = 5,
                    last_attempt_at = ?,
                    last_error = ?
                WHERE id = ?
                """,
                OffsetDateTime.ofInstant(Instant.parse("2026-03-27T10:18:00Z"), ZoneOffset.UTC),
                "Kafka unavailable",
                eventId
        );

        inventoryOutboxService.enqueueOutbox("STOCK_ADJUSTMENT", "8805", "inventory.adjustment.posted", "101", adjustmentEvent(
                8805L,
                "event-adjustment-failed-2",
                "corr-adjustment-failed-2",
                "inventory.adjustment.posted:adjustment:8805",
                new BigDecimal("5.0000")
        ));

        assertThat(count("""
                SELECT COUNT(*)
                FROM inventory.outbox_event
                WHERE aggregate_type = 'STOCK_ADJUSTMENT'
                  AND aggregate_id = '8805'
                  AND event_type = 'inventory.adjustment.posted'
                """)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.outbox_event
                WHERE id = ?
                """, String.class, eventId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT retry_count
                FROM inventory.outbox_event
                WHERE id = ?
                """, Integer.class, eventId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT last_error
                FROM inventory.outbox_event
                WHERE id = ?
                """, String.class, eventId)).isNull();
    }

    private InventoryAdjustmentPostedEvent adjustmentEvent(
            Long stockAdjustmentId,
            String eventId,
            String correlationId,
            String idempotencyKey,
            BigDecimal qtyChange
    ) {
        return new InventoryAdjustmentPostedEvent(
                eventId,
                "inventory.adjustment.posted",
                Instant.parse("2026-03-27T10:15:00Z"),
                "inventory-service",
                correlationId,
                idempotencyKey,
                stockAdjustmentId,
                1L,
                101L,
                200L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:15:00Z"),
                7L,
                "IN",
                "CORRECTION",
                qtyChange,
                new BigDecimal("10000.00"),
                "STOCK_ADJUSTMENT",
                stockAdjustmentId.toString()
        );
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
