package com.fern.financeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.service.FinanceProcurementConsumer;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class FinanceProcurementConsumerHardeningTest {
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
        registry.add("fern.security.jwt.secret", () -> "test-jwt-secret-that-is-at-least-32-characters-long");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private FinanceProcurementConsumer consumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("projectionJdbcTemplate")
    private NamedParameterJdbcTemplate projectionJdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    finance.outbox_event,
                    finance.expense_inventory_purchase,
                    finance.expense_record,
                    finance.integration_event
                RESTART IDENTITY CASCADE
                """);
        projectionJdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    finance_projection.accounting_posting_projection,
                    finance_projection.reconciliation_snapshot
                """);
    }

    @Test
    void shouldProcessGoodsReceiptReplayConcurrentlyOnlyOnce() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "gr-race-1",
                "idem-gr-race-1",
                9001L,
                Instant.parse("2026-03-27T10:00:00Z")
        );
        String payload = objectMapper.writeValueAsString(event);

        runConcurrently(() -> consumer.consumeGoodsReceiptPosted(payload));

        assertThat(count("""
                SELECT COUNT(*) FROM finance.expense_record WHERE source_event_id = 'gr-race-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.expense_inventory_purchase WHERE goods_receipt_id = 9001
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id IN (SELECT id::text FROM finance.expense_record WHERE source_event_id = 'gr-race-1')
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event WHERE source_event_id = 'gr-race-1' AND status = 'PROCESSED'
                """)).isEqualTo(1);
    }

    @Test
    void shouldIgnoreBurstDuplicateGoodsReceiptReplayAndPreserveCorrelationInOutbox() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "gr-burst-1",
                "idem-gr-burst-1",
                9002L,
                Instant.parse("2026-03-27T11:00:00Z")
        );
        String payload = objectMapper.writeValueAsString(event);

        runBurst(5, () -> consumer.consumeGoodsReceiptPosted(payload));

        assertThat(count("""
                SELECT COUNT(*) FROM finance.expense_record WHERE source_event_id = 'gr-burst-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id IN (SELECT id::text FROM finance.expense_record WHERE source_event_id = 'gr-burst-1')
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event
                WHERE event_type = 'procurement.goods_receipt.posted'
                  AND idempotency_key = 'idem-gr-burst-1'
                """)).isEqualTo(1);

        String correlationId = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT payload ->> 'correlationId'
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id IN (SELECT id::text FROM finance.expense_record WHERE source_event_id = 'gr-burst-1')
                """, String.class);
        Long expenseRecordId = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT id
                FROM finance.expense_record
                WHERE source_event_id = 'gr-burst-1'
                """, Long.class);
        String eventId = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT payload ->> 'eventId'
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id IN (SELECT id::text FROM finance.expense_record WHERE source_event_id = 'gr-burst-1')
                """, String.class);
        String idempotencyKey = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT payload ->> 'idempotencyKey'
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id IN (SELECT id::text FROM finance.expense_record WHERE source_event_id = 'gr-burst-1')
                """, String.class);
        assertThat(correlationId).isEqualTo("corr-gr-burst-1");
        assertThat(eventId).matches("\\d+");
        assertThat(idempotencyKey).isEqualTo("finance.expense.posted:expense:" + expenseRecordId);
    }

    @Test
    void shouldProcessSupplierPaymentReplayConcurrentlyOnlyOnce() throws Exception {
        SupplierPaymentRecordedEvent event = supplierPaymentEvent(
                "payment-race-1",
                "idem-payment-race-1",
                9101L,
                Instant.parse("2026-03-27T12:00:00Z"),
                new BigDecimal("41.25")
        );
        String payload = objectMapper.writeValueAsString(event);

        runConcurrently(() -> consumer.consumeSupplierPaymentRecorded(payload));

        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.accounting_posting_projection WHERE source_event_id = 'payment-race-1'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot WHERE source_event_id = 'payment-race-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event WHERE source_event_id = 'payment-race-1' AND status = 'PROCESSED'
                """)).isEqualTo(1);
    }

    @Test
    void shouldIgnoreDuplicateSettlementAcrossDifferentSourceEventsWithoutSecondProjection() throws Exception {
        SupplierPaymentRecordedEvent first = supplierPaymentEvent(
                "payment-dup-1",
                "idem-payment-dup",
                9201L,
                Instant.parse("2026-03-27T12:00:00Z"),
                new BigDecimal("88.50")
        );
        SupplierPaymentRecordedEvent replayedDuplicate = supplierPaymentEvent(
                "payment-dup-2",
                "idem-payment-dup",
                9201L,
                Instant.parse("2026-03-27T12:00:00Z"),
                new BigDecimal("88.50")
        );

        consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(first));
        consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(replayedDuplicate));

        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.accounting_posting_projection
                WHERE idempotency_key = 'idem-payment-dup'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot
                WHERE idempotency_key = 'idem-payment-dup'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event
                WHERE source_event_id IN ('payment-dup-1', 'payment-dup-2')
                """))
                .as("Duplicate settlement replay should not persist a second finance.integration_event row")
                .isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event
                WHERE event_type = 'procurement.supplier.payment.recorded'
                  AND idempotency_key = 'idem-payment-dup'
                """)).isEqualTo(1);
    }

    @Test
    void shouldIgnoreConcurrentDuplicateSettlementAcrossDifferentSourceEvents() throws Exception {
        SupplierPaymentRecordedEvent first = supplierPaymentEvent(
                "payment-dup-race-1",
                "idem-payment-dup-race",
                9202L,
                Instant.parse("2026-03-27T13:00:00Z"),
                new BigDecimal("54.10")
        );
        SupplierPaymentRecordedEvent replayedDuplicate = supplierPaymentEvent(
                "payment-dup-race-2",
                "idem-payment-dup-race",
                9202L,
                Instant.parse("2026-03-27T13:00:00Z"),
                new BigDecimal("54.10")
        );

        runConcurrently(
                () -> consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(first)),
                () -> consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(replayedDuplicate))
        );

        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.accounting_posting_projection
                WHERE idempotency_key = 'idem-payment-dup-race'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot
                WHERE idempotency_key = 'idem-payment-dup-race'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event
                WHERE event_type = 'procurement.supplier.payment.recorded'
                  AND idempotency_key = 'idem-payment-dup-race'
                """)).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateSettlementWhenIdempotencyKeyIsReusedForDifferentPayload() throws Exception {
        SupplierPaymentRecordedEvent first = supplierPaymentEvent(
                "payment-conflict-1",
                "idem-payment-conflict",
                9203L,
                Instant.parse("2026-03-27T13:30:00Z"),
                new BigDecimal("54.10")
        );
        SupplierPaymentRecordedEvent conflictingReplay = supplierPaymentEvent(
                "payment-conflict-2",
                "idem-payment-conflict",
                9203L,
                Instant.parse("2026-03-27T13:30:00Z"),
                new BigDecimal("55.10")
        );

        consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(first));

        assertThatThrownBy(() -> consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(conflictingReplay)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Finance integration idempotency conflict");

        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event
                WHERE event_type = 'procurement.supplier.payment.recorded'
                  AND idempotency_key = 'idem-payment-conflict'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*) FROM finance_projection.accounting_posting_projection
                WHERE idempotency_key = 'idem-payment-conflict'
                """)).isEqualTo(1);
    }

    @Test
    void shouldRejectGoodsReceiptReplayWhenIdempotencyKeyIsReusedForDifferentPayload() throws Exception {
        ProcurementGoodsReceiptPostedEvent first = goodsReceiptEvent(
                "gr-conflict-1",
                "idem-gr-conflict",
                9003L,
                Instant.parse("2026-03-27T11:30:00Z")
        );
        ProcurementGoodsReceiptPostedEvent conflictingReplay = new ProcurementGoodsReceiptPostedEvent(
                "gr-conflict-2",
                first.eventType(),
                first.occurredAt(),
                first.sourceService(),
                first.correlationId(),
                first.idempotencyKey(),
                first.goodsReceiptId(),
                first.purchaseOrderId(),
                first.regionId(),
                first.outletId(),
                first.businessDate(),
                first.postedAt(),
                first.postedByUserId(),
                List.of(new GoodsReceiptPostedLine(
                        first.lines().getFirst().ingredientId(),
                        new BigDecimal("6.0000"),
                        first.lines().getFirst().unitCost(),
                        first.lines().getFirst().sourceLineId()
                ))
        );

        consumer.consumeGoodsReceiptPosted(objectMapper.writeValueAsString(first));

        assertThatThrownBy(() -> consumer.consumeGoodsReceiptPosted(objectMapper.writeValueAsString(conflictingReplay)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Finance integration idempotency conflict");

        assertThat(count("""
                SELECT COUNT(*) FROM finance.integration_event
                WHERE event_type = 'procurement.goods_receipt.posted'
                  AND idempotency_key = 'idem-gr-conflict'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM finance.expense_record
                WHERE source_event_id = 'gr-conflict-1'
                """)).isEqualTo(1);
    }

    @Test
    void shouldUsePaymentTimeAsBusinessDateForDelayedSupplierPaymentEvent() throws Exception {
        SupplierPaymentRecordedEvent event = supplierPaymentEvent(
                "payment-late-1",
                "idem-payment-late-1",
                9301L,
                Instant.parse("2026-03-27T23:45:00Z"),
                new BigDecimal("19.75")
        );

        consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(event));

        LocalDate businessDate = projectionJdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT business_date
                FROM finance_projection.reconciliation_snapshot
                WHERE source_event_id = 'payment-late-1'
                """, LocalDate.class);

        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 3, 27));
    }

    @Test
    void shouldRejectPoisonSupplierPaymentPayloadWithoutPersistingState() {
        assertThatThrownBy(() -> consumer.consumeSupplierPaymentRecorded("{not-json"))
                .isInstanceOf(JsonProcessingException.class);

        assertThat(count("SELECT COUNT(*) FROM finance.integration_event")).isZero();
        assertThat(countProjection("SELECT COUNT(*) FROM finance_projection.accounting_posting_projection")).isZero();
        assertThat(countProjection("SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot")).isZero();
    }

    @Test
    void shouldRejectSemanticPoisonGoodsReceiptEventWithoutPersistingState() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                "gr-poison-negative-1",
                "procurement.goods_receipt.posted",
                Instant.parse("2026-03-27T16:00:00Z"),
                "procurement-service",
                "corr-gr-poison-negative-1",
                "idem-gr-poison-negative-1",
                9601L,
                8601L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T16:00:00Z"),
                3L,
                List.of(new GoodsReceiptPostedLine(200L, new BigDecimal("-3.0000"), new BigDecimal("12.50"), 3001L))
        );

        assertThatThrownBy(() -> consumer.consumeGoodsReceiptPosted(objectMapper.writeValueAsString(event)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThat(count("SELECT COUNT(*) FROM finance.integration_event")).isZero();
        assertThat(count("SELECT COUNT(*) FROM finance.expense_record")).isZero();
        assertThat(count("SELECT COUNT(*) FROM finance.outbox_event")).isZero();
    }

    @Test
    void shouldRejectSemanticPoisonSupplierPaymentEventWithoutPersistingState() throws Exception {
        SupplierPaymentRecordedEvent event = supplierPaymentEvent(
                "payment-poison-negative-1",
                "idem-payment-poison-negative-1",
                9501L,
                Instant.parse("2026-03-27T15:00:00Z"),
                new BigDecimal("-41.25")
        );

        assertThatThrownBy(() -> consumer.consumeSupplierPaymentRecorded(objectMapper.writeValueAsString(event)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThat(count("SELECT COUNT(*) FROM finance.integration_event")).isZero();
        assertThat(countProjection("SELECT COUNT(*) FROM finance_projection.accounting_posting_projection")).isZero();
        assertThat(countProjection("SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot")).isZero();
    }

    private ProcurementGoodsReceiptPostedEvent goodsReceiptEvent(
            String eventId,
            String idempotencyKey,
            Long goodsReceiptId,
            Instant postedAt
    ) {
        return new ProcurementGoodsReceiptPostedEvent(
                eventId,
                "procurement.goods_receipt.posted",
                postedAt,
                "procurement-service",
                "corr-" + eventId,
                idempotencyKey,
                goodsReceiptId,
                8001L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                postedAt,
                2L,
                List.of(new GoodsReceiptPostedLine(200L, new BigDecimal("3.0000"), new BigDecimal("12.50"), 3001L))
        );
    }

    private SupplierPaymentRecordedEvent supplierPaymentEvent(
            String eventId,
            String idempotencyKey,
            Long paymentId,
            Instant paymentTime,
            BigDecimal amount
    ) {
        return new SupplierPaymentRecordedEvent(
                eventId,
                "procurement.supplier.payment.recorded",
                Instant.parse("2026-03-28T08:00:00Z"),
                "procurement-service",
                "corr-" + eventId,
                idempotencyKey,
                paymentId,
                7001L,
                paymentTime,
                amount,
                "VND",
                List.of(new SupplierPaymentAllocation(8201L, amount)),
                3L
        );
    }

    private void runConcurrently(ThrowingRunnable runnable) throws Exception {
        runConcurrently(runnable, runnable);
    }

    private void runBurst(int concurrency, ThrowingRunnable runnable) throws Exception {
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, concurrency)
                    .mapToObj(index -> executor.submit(() -> {
                        runWithBarrier(ready, start, runnable);
                        return (Object) null;
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get();
            }
        }
    }

    private void runConcurrently(ThrowingRunnable firstRunnable, ThrowingRunnable secondRunnable) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runWithBarrier(ready, start, firstRunnable));
            Future<?> second = executor.submit(() -> runWithBarrier(ready, start, secondRunnable));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get();
            second.get();
        }
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

    private int count(String sql) {
        return jdbcTemplate.getJdbcTemplate().queryForObject(sql, Integer.class);
    }

    private int countProjection(String sql) {
        return projectionJdbcTemplate.getJdbcTemplate().queryForObject(sql, Integer.class);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
