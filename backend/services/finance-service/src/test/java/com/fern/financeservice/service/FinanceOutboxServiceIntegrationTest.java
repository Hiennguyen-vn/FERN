package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class FinanceOutboxServiceIntegrationTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.projection-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.projection-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.projection-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "finance-outbox-test-secret-012345678901234567890123456");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private FinanceOutboxService financeOutboxService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE finance.outbox_event
                """);
    }

    @Test
    void shouldTreatDuplicateExpensePostedEmissionAsSingleOutboxEvent() {
        financeOutboxService.emitExpensePosted(
                8801L,
                1L,
                101L,
                null,
                null,
                LocalDate.of(2026, 3, 27),
                "INVENTORY_PURCHASE",
                new BigDecimal("42.25"),
                "corr-expense-outbox-1",
                "GOODS_RECEIPT",
                "9001"
        );
        financeOutboxService.emitExpensePosted(
                8801L,
                1L,
                101L,
                null,
                null,
                LocalDate.of(2026, 3, 27),
                "INVENTORY_PURCHASE",
                new BigDecimal("42.25"),
                "corr-expense-outbox-2",
                "GOODS_RECEIPT",
                "9001"
        );

        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '8801'
                  AND event_type = 'finance.expense.posted'
                """)).isEqualTo(1);
        assertThat(outboxPayloadField("EXPENSE_RECORD", "8801", "finance.expense.posted", "eventId"))
                .matches("\\d+");
        assertThat(outboxPayloadField("EXPENSE_RECORD", "8801", "finance.expense.posted", "idempotencyKey"))
                .isEqualTo("finance.expense.posted:expense:8801");
    }

    @Test
    void shouldRejectDuplicateExpensePostedEmissionWhenPayloadChanges() {
        financeOutboxService.emitExpensePosted(
                8802L,
                1L,
                101L,
                null,
                null,
                LocalDate.of(2026, 3, 27),
                "INVENTORY_PURCHASE",
                new BigDecimal("42.25"),
                "corr-expense-outbox-conflict-1",
                "GOODS_RECEIPT",
                "9002"
        );

        assertThatThrownBy(() -> financeOutboxService.emitExpensePosted(
                8802L,
                1L,
                101L,
                null,
                null,
                LocalDate.of(2026, 3, 27),
                "INVENTORY_PURCHASE",
                new BigDecimal("99.99"),
                "corr-expense-outbox-conflict-2",
                "GOODS_RECEIPT",
                "9002"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Finance outbox idempotency conflict");

        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '8802'
                  AND event_type = 'finance.expense.posted'
                """)).isEqualTo(1);
    }

    @Test
    void shouldDeduplicateConcurrentExpensePostedEmission() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runWithBarrier(ready, start, () -> financeOutboxService.emitExpensePosted(
                    8803L,
                    1L,
                    101L,
                    null,
                    null,
                    LocalDate.of(2026, 3, 27),
                    "INVENTORY_PURCHASE",
                    new BigDecimal("42.25"),
                    "corr-expense-outbox-race-1",
                    "GOODS_RECEIPT",
                    "9003"
            )));
            Future<?> second = executor.submit(() -> runWithBarrier(ready, start, () -> financeOutboxService.emitExpensePosted(
                    8803L,
                    1L,
                    101L,
                    null,
                    null,
                    LocalDate.of(2026, 3, 27),
                    "INVENTORY_PURCHASE",
                    new BigDecimal("42.25"),
                    "corr-expense-outbox-race-2",
                    "GOODS_RECEIPT",
                    "9003"
            )));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get();
            second.get();
        }

        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '8803'
                  AND event_type = 'finance.expense.posted'
                """)).isEqualTo(1);
    }

    @Test
    void shouldReviveFailedExpensePostedOutboxWhenEquivalentReplayArrives() {
        financeOutboxService.emitExpensePosted(
                8804L,
                1L,
                101L,
                null,
                null,
                LocalDate.of(2026, 3, 27),
                "INVENTORY_PURCHASE",
                new BigDecimal("42.25"),
                "corr-expense-outbox-failed-1",
                "GOODS_RECEIPT",
                "9004"
        );
        UUID eventId = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT id
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '8804'
                  AND event_type = 'finance.expense.posted'
                """, UUID.class);
        jdbcTemplate.getJdbcTemplate().update("""
                UPDATE finance.outbox_event
                SET status = 'FAILED',
                    retry_count = 5,
                    last_attempt_at = ?,
                    last_error = ?
                WHERE id = ?
                """,
                OffsetDateTime.ofInstant(Instant.parse("2026-03-27T11:20:00Z"), ZoneOffset.UTC),
                "Kafka unavailable",
                eventId
        );

        financeOutboxService.emitExpensePosted(
                8804L,
                1L,
                101L,
                null,
                null,
                LocalDate.of(2026, 3, 27),
                "INVENTORY_PURCHASE",
                new BigDecimal("42.25"),
                "corr-expense-outbox-failed-2",
                "GOODS_RECEIPT",
                "9004"
        );

        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '8804'
                  AND event_type = 'finance.expense.posted'
                """)).isEqualTo(1);
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT status
                FROM finance.outbox_event
                WHERE id = ?
                """, String.class, eventId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT retry_count
                FROM finance.outbox_event
                WHERE id = ?
                """, Integer.class, eventId)).isZero();
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT last_error
                FROM finance.outbox_event
                WHERE id = ?
                """, String.class, eventId)).isNull();
    }

    @Test
    void shouldRejectOutboxReplayWhenStoredPartitionKeyChanges() {
        financeOutboxService.enqueueOutbox(
                "EXPENSE_RECORD",
                "8805",
                "finance.expense.posted",
                "1",
                Map.of("amount", "42.25", "eventId", "event-1", "idempotencyKey", "key-1")
        );
        jdbcTemplate.getJdbcTemplate().update("""
                UPDATE finance.outbox_event
                SET partition_key = '2'
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id = '8805'
                  AND event_type = 'finance.expense.posted'
                """);

        assertThatThrownBy(() -> financeOutboxService.enqueueOutbox(
                "EXPENSE_RECORD",
                "8805",
                "finance.expense.posted",
                "1",
                Map.of("amount", "42.25", "eventId", "event-2", "idempotencyKey", "key-2")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Finance outbox idempotency conflict");
    }

    private int count(String sql) {
        return jdbcTemplate.getJdbcTemplate().queryForObject(sql, Integer.class);
    }

    private String outboxPayloadField(String aggregateType, String aggregateId, String eventType, String field) {
        return jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT payload ->> '%s'
                FROM finance.outbox_event
                WHERE aggregate_type = '%s'
                  AND aggregate_id = '%s'
                  AND event_type = '%s'
                ORDER BY created_at, id
                LIMIT 1
                """.formatted(field, aggregateType, aggregateId, eventType), String.class);
    }

    private void runWithBarrier(CountDownLatch ready, CountDownLatch start, ThrowingRunnable runnable) {
        try {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            runnable.run();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
