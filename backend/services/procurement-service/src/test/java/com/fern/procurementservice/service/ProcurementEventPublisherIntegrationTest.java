package com.fern.procurementservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.platform.testsupport.JsonTestSupport;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ProcurementEventPublisherIntegrationTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.clients.org.base-url", () -> "http://localhost");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "procurement-event-publisher-secret-012345678901234567890123");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private ProcurementEventPublisher procurementEventPublisher;

    @Autowired
    private ProcurementJdbcRepository procurementJdbcRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    procurement.supplier_payment_allocation,
                    procurement.supplier_payment,
                    procurement.supplier_invoice_line,
                    procurement.supplier_invoice,
                    procurement.goods_receipt_line,
                    procurement.goods_receipt,
                    procurement.purchase_order_line,
                    procurement.purchase_order,
                    procurement.outbox_event
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldPersistGoodsReceiptPostedPayloadToSharedFixtureContract() throws Exception {
        seedGoodsReceiptFixture();

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(
                procurementJdbcRepository.requireGoodsReceipt(33L),
                principal(7L),
                "corr-gr-fixture"
        );

        String payload = outboxPayload("GOODS_RECEIPT", "33", "procurement.goods_receipt.posted");
        assertThat(JsonTestSupport.jsonEquals(expectedFixtureJson(
                "procurement.goods_receipt.posted.json",
                payload
        ), payload)).isTrue();
    }

    @Test
    void shouldPersistSupplierPaymentRecordedPayloadToSharedFixtureContract() throws Exception {
        seedSupplierPaymentFixture();

        procurementEventPublisher.enqueueSupplierPaymentRecordedEvent(71L, principal(9L), "corr-payment-fixture");

        String payload = outboxPayload("SUPPLIER_PAYMENT", "71", "procurement.supplier.payment.recorded");
        assertThat(JsonTestSupport.jsonEquals(expectedFixtureJson(
                "procurement.supplier.payment.recorded.json",
                payload
        ), payload)).isTrue();
    }

    @Test
    void shouldDeduplicateGoodsReceiptOutboxAndUseDeterministicIdempotencyKey() {
        long purchaseOrderId = seedPurchaseOrder();
        long goodsReceiptId = seedGoodsReceipt(purchaseOrderId, new BigDecimal("3.0000"), new BigDecimal("12.5000"));
        GoodsReceiptRecord record = procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId);

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(record, principal(), "corr-gr-outbox-1");
        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(record, principal(), "corr-gr-outbox-2");

        assertThat(count("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE aggregate_type = 'GOODS_RECEIPT'
                  AND aggregate_id = '%d'
                  AND event_type = 'procurement.goods_receipt.posted'
                """.formatted(goodsReceiptId))).isEqualTo(1);
        assertThat(outboxPayloadField("GOODS_RECEIPT", Long.toString(goodsReceiptId), "procurement.goods_receipt.posted", "idempotencyKey"))
                .isEqualTo("procurement.goods_receipt.posted:receipt:" + goodsReceiptId);
        assertThat(outboxPayloadField("GOODS_RECEIPT", Long.toString(goodsReceiptId), "procurement.goods_receipt.posted", "postedAt"))
                .isEqualTo(outboxPayloadField("GOODS_RECEIPT", Long.toString(goodsReceiptId), "procurement.goods_receipt.posted", "occurredAt"));
    }

    @Test
    void shouldRejectGoodsReceiptOutboxConflictWhenPayloadChanges() {
        long purchaseOrderId = seedPurchaseOrder();
        long goodsReceiptId = seedGoodsReceipt(purchaseOrderId, new BigDecimal("3.0000"), new BigDecimal("12.5000"));
        GoodsReceiptRecord record = procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId);

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(record, principal(), "corr-gr-outbox-conflict-1");
        jdbcTemplate.update("""
                UPDATE procurement.goods_receipt_line
                SET unit_cost = 15.0000,
                    line_total = 45.00,
                    updated_at = CURRENT_TIMESTAMP
                WHERE goods_receipt_id = ?
                """, goodsReceiptId);

        assertThatThrownBy(() -> procurementEventPublisher.enqueueGoodsReceiptPostedEvent(
                procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId),
                principal(),
                "corr-gr-outbox-conflict-2"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Procurement outbox idempotency conflict");

        assertThat(count("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE aggregate_type = 'GOODS_RECEIPT'
                  AND aggregate_id = '%d'
                  AND event_type = 'procurement.goods_receipt.posted'
                """.formatted(goodsReceiptId))).isEqualTo(1);
    }

    @Test
    void shouldDeduplicateGoodsReceiptOutboxWhenFirstPublishUsesStalePreCommitRecord() {
        long purchaseOrderId = seedPurchaseOrder();
        long goodsReceiptId = seedReceivedGoodsReceipt(purchaseOrderId, new BigDecimal("3.0000"), new BigDecimal("12.5000"));
        GoodsReceiptRecord staleRecord = procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId);
        jdbcTemplate.update("""
                UPDATE procurement.goods_receipt
                SET status = 'POSTED',
                    posted_at = TIMESTAMPTZ '2026-03-27T10:15:00Z',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, goodsReceiptId);

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(staleRecord, principal(), "corr-gr-stale-1");
        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(
                procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId),
                principal(),
                "corr-gr-stale-2"
        );

        assertThat(count("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE aggregate_type = 'GOODS_RECEIPT'
                  AND aggregate_id = '%d'
                  AND event_type = 'procurement.goods_receipt.posted'
                """.formatted(goodsReceiptId))).isEqualTo(1);
        assertThat(outboxPayloadField("GOODS_RECEIPT", Long.toString(goodsReceiptId), "procurement.goods_receipt.posted", "postedAt"))
                .isEqualTo(outboxPayloadField("GOODS_RECEIPT", Long.toString(goodsReceiptId), "procurement.goods_receipt.posted", "occurredAt"));
    }

    @Test
    void shouldDeduplicateSupplierPaymentOutboxAndUseDeterministicIdempotencyKey() {
        long supplierPaymentId = seedSupplierPayment();

        procurementEventPublisher.enqueueSupplierPaymentRecordedEvent(supplierPaymentId, principal(), "corr-payment-outbox-1");
        procurementEventPublisher.enqueueSupplierPaymentRecordedEvent(supplierPaymentId, principal(), "corr-payment-outbox-2");

        assertThat(count("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE aggregate_type = 'SUPPLIER_PAYMENT'
                  AND aggregate_id = '%d'
                  AND event_type = 'procurement.supplier.payment.recorded'
                """.formatted(supplierPaymentId))).isEqualTo(1);
        assertThat(outboxPayloadField("SUPPLIER_PAYMENT", Long.toString(supplierPaymentId), "procurement.supplier.payment.recorded", "idempotencyKey"))
                .isEqualTo("procurement.supplier.payment.recorded:payment:" + supplierPaymentId);
        assertThat(outboxPayloadField("SUPPLIER_PAYMENT", Long.toString(supplierPaymentId), "procurement.supplier.payment.recorded", "occurredAt"))
                .isEqualTo(outboxPayloadField("SUPPLIER_PAYMENT", Long.toString(supplierPaymentId), "procurement.supplier.payment.recorded", "paymentTime"));
    }

    @Test
    void shouldReviveFailedGoodsReceiptOutboxWhenEquivalentReplayArrives() {
        long purchaseOrderId = seedPurchaseOrder();
        long goodsReceiptId = seedGoodsReceipt(purchaseOrderId, new BigDecimal("3.0000"), new BigDecimal("12.5000"));
        GoodsReceiptRecord record = procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId);

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(record, principal(), "corr-gr-outbox-failed-1");
        UUID eventId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM procurement.outbox_event
                WHERE aggregate_type = 'GOODS_RECEIPT'
                  AND aggregate_id = ?
                  AND event_type = 'procurement.goods_receipt.posted'
                """, UUID.class, Long.toString(goodsReceiptId));
        jdbcTemplate.update("""
                UPDATE procurement.outbox_event
                SET status = 'FAILED',
                    retry_count = 5,
                    last_attempt_at = ?,
                    last_error = ?
                WHERE id = ?
                """,
                OffsetDateTime.ofInstant(Instant.parse("2026-03-27T10:20:00Z"), ZoneOffset.UTC),
                "Kafka unavailable",
                eventId
        );

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(
                procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId),
                principal(),
                "corr-gr-outbox-failed-2"
        );

        assertThat(count("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE aggregate_type = 'GOODS_RECEIPT'
                  AND aggregate_id = '%d'
                  AND event_type = 'procurement.goods_receipt.posted'
                """.formatted(goodsReceiptId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.outbox_event
                WHERE id = ?
                """, String.class, eventId)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT retry_count
                FROM procurement.outbox_event
                WHERE id = ?
                """, Integer.class, eventId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT last_error
                FROM procurement.outbox_event
                WHERE id = ?
                """, String.class, eventId)).isNull();
    }

    @Test
    void shouldRejectGoodsReceiptOutboxReplayWhenStoredPartitionKeyChanges() {
        long purchaseOrderId = seedPurchaseOrder();
        long goodsReceiptId = seedGoodsReceipt(purchaseOrderId, new BigDecimal("3.0000"), new BigDecimal("12.5000"));
        GoodsReceiptRecord record = procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId);

        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(record, principal(), "corr-gr-outbox-partition-1");
        jdbcTemplate.update("""
                UPDATE procurement.outbox_event
                SET partition_key = '999'
                WHERE aggregate_type = 'GOODS_RECEIPT'
                  AND aggregate_id = ?
                  AND event_type = 'procurement.goods_receipt.posted'
                """, Long.toString(goodsReceiptId));

        assertThatThrownBy(() -> procurementEventPublisher.enqueueGoodsReceiptPostedEvent(
                procurementJdbcRepository.requireGoodsReceipt(goodsReceiptId),
                principal(),
                "corr-gr-outbox-partition-2"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Procurement outbox idempotency conflict");
    }

    private FernPrincipal principal() {
        return principal(7L);
    }

    private FernPrincipal principal(long userId) {
        return new FernPrincipal(
                userId,
                "procurement-outbox-tester",
                Set.of("procurement"),
                Set.of(),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "procurement-outbox-tester-jti"
        );
    }

    private void seedGoodsReceiptFixture() {
        jdbcTemplate.update("""
                INSERT INTO procurement.purchase_order (
                    id, po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status,
                    subtotal_amount, tax_amount, total_amount, created_at, updated_at
                ) VALUES (
                    44, 'PO-FIXTURE-044', 1, 101, 7001, DATE '2026-03-29', DATE '2026-03-30', 'PARTIALLY_RECEIVED',
                    80.00, 0, 80.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO procurement.goods_receipt (
                    id, receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                    status, total_amount, created_at, updated_at, received_at, posted_at
                ) VALUES (
                    33, 'GR-FIXTURE-033', 44, 1, 101, 7001, TIMESTAMPTZ '2026-03-29T09:30:00Z', DATE '2026-03-29',
                    'POSTED', 80.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TIMESTAMPTZ '2026-03-29T09:45:00Z',
                    TIMESTAMPTZ '2026-03-29T10:00:00Z'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO procurement.goods_receipt_line (
                    id, goods_receipt_id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost,
                    line_total, created_at, updated_at
                ) VALUES
                    (88, 33, NULL, 200, 'KG', 10.0000, 5.0000, 50.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (89, 33, NULL, 201, 'KG', 4.0000, 7.5000, 30.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    private void seedSupplierPaymentFixture() {
        jdbcTemplate.update("""
                INSERT INTO procurement.supplier_invoice (
                    id, invoice_number, supplier_id, currency_code, invoice_date, subtotal, tax_amount,
                    total_amount, status, created_at, updated_at
                ) VALUES
                    (501, 'INV-FIXTURE-501', 22, 'VND', DATE '2026-03-28', 50.00, 0, 50.00, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (502, 'INV-FIXTURE-502', 22, 'VND', DATE '2026-03-28', 25.00, 0, 25.00, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO procurement.supplier_payment (
                    id, payment_number, supplier_id, currency_code, payment_method, amount, payment_time,
                    transaction_ref, created_by_user_id, created_at, updated_at
                ) VALUES (
                    71, 'PAY-FIXTURE-071', 22, 'VND', 'BANK_TRANSFER', 75.00, TIMESTAMPTZ '2026-03-29T15:00:00Z',
                    'txn-fixture-71', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO procurement.supplier_payment_allocation (
                    supplier_payment_id, supplier_invoice_id, allocated_amount, created_at, updated_at
                ) VALUES
                    (71, 501, 50.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (71, 502, 25.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    private String outboxPayload(String aggregateType, String aggregateId, String eventType) {
        return jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM procurement.outbox_event
                WHERE aggregate_type = ?
                  AND aggregate_id = ?
                  AND event_type = ?
                ORDER BY created_at, id
                LIMIT 1
                """, String.class, aggregateType, aggregateId, eventType);
    }

    private String expectedFixtureJson(String fixtureName, String actualJson) throws Exception {
        ObjectNode expected = (ObjectNode) objectMapper.readTree(loadFixture(fixtureName));
        JsonNode actual = objectMapper.readTree(actualJson);
        expected.put("eventId", actual.path("eventId").asText());
        return objectMapper.writeValueAsString(expected);
    }

    private String loadFixture(String fixtureName) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/kafka-contract-fixtures/" + fixtureName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private long seedPurchaseOrder() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO procurement.purchase_order (
                    po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status,
                    subtotal_amount, tax_amount, total_amount, created_at, updated_at
                ) VALUES (
                    'PO-000001', 1, 101, 7001, DATE '2026-03-27', DATE '2026-03-28', 'ORDERED',
                    37.50, 0, 37.50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, Long.class);
    }

    private long seedGoodsReceipt(long purchaseOrderId, BigDecimal qtyReceived, BigDecimal unitCost) {
        long goodsReceiptId = jdbcTemplate.queryForObject("""
                INSERT INTO procurement.goods_receipt (
                    receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                    status, total_amount, created_at, updated_at, received_at, posted_at
                ) VALUES (
                    'GR-000001', ?, 1, 101, 7001, TIMESTAMPTZ '2026-03-27T09:00:00Z', DATE '2026-03-27',
                    'POSTED', 37.50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TIMESTAMPTZ '2026-03-27T09:30:00Z',
                    TIMESTAMPTZ '2026-03-27T10:15:00Z'
                )
                RETURNING id
                """, Long.class, purchaseOrderId);
        jdbcTemplate.update("""
                INSERT INTO procurement.goods_receipt_line (
                    goods_receipt_id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost,
                    line_total, created_at, updated_at
                ) VALUES (
                    ?, NULL, 200, 'KG', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, goodsReceiptId, qtyReceived, unitCost, qtyReceived.multiply(unitCost).setScale(2));
        return goodsReceiptId;
    }

    private long seedReceivedGoodsReceipt(long purchaseOrderId, BigDecimal qtyReceived, BigDecimal unitCost) {
        long goodsReceiptId = jdbcTemplate.queryForObject("""
                INSERT INTO procurement.goods_receipt (
                    receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                    status, total_amount, created_at, updated_at, received_at
                ) VALUES (
                    'GR-000002', ?, 1, 101, 7001, TIMESTAMPTZ '2026-03-27T09:00:00Z', DATE '2026-03-27',
                    'RECEIVED', 37.50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TIMESTAMPTZ '2026-03-27T09:30:00Z'
                )
                RETURNING id
                """, Long.class, purchaseOrderId);
        jdbcTemplate.update("""
                INSERT INTO procurement.goods_receipt_line (
                    goods_receipt_id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost,
                    line_total, created_at, updated_at
                ) VALUES (
                    ?, NULL, 200, 'KG', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, goodsReceiptId, qtyReceived, unitCost, qtyReceived.multiply(unitCost).setScale(2));
        return goodsReceiptId;
    }

    private long seedSupplierPayment() {
        long supplierInvoiceId = jdbcTemplate.queryForObject("""
                INSERT INTO procurement.supplier_invoice (
                    supplier_id, region_id, outlet_id, currency_code, invoice_number, invoice_date, due_date,
                    subtotal, tax_amount, total_amount, status, created_at, updated_at, approved_at
                ) VALUES (
                    7001, 1, 101, 'VND', 'INV-000001', DATE '2026-03-27', DATE '2026-04-10',
                    125.50, 0, 125.50, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TIMESTAMPTZ '2026-03-27T11:00:00Z'
                )
                RETURNING id
                """, Long.class);
        long paymentId = jdbcTemplate.queryForObject("""
                INSERT INTO procurement.supplier_payment (
                    payment_number, supplier_id, currency_code, payment_method, amount, payment_time, transaction_ref,
                    note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    'SP-000001', 7001, 'VND', 'BANK_TRANSFER', 125.50, TIMESTAMPTZ '2026-03-27T12:00:00Z', 'TXN-001',
                    'payment note', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO procurement.supplier_payment_allocation (
                    supplier_payment_id, supplier_invoice_id, allocated_amount, note, created_at, updated_at
                ) VALUES (
                    ?, ?, 125.50, 'allocation note', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, paymentId, supplierInvoiceId);
        return paymentId;
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String outboxPayloadField(String aggregateType, String aggregateId, String eventType, String field) {
        return jdbcTemplate.queryForObject("""
                SELECT payload ->> '%s'
                FROM procurement.outbox_event
                WHERE aggregate_type = '%s'
                  AND aggregate_id = '%s'
                  AND event_type = '%s'
                ORDER BY created_at, id
                LIMIT 1
                """.formatted(field, aggregateType, aggregateId, eventType), String.class);
    }
}
