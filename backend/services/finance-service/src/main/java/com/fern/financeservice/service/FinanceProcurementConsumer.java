package com.fern.financeservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FinanceProcurementConsumer {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate projectionJdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;
    private final FinanceOutboxService financeOutboxService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate projectionTransactionTemplate;

    public FinanceProcurementConsumer(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("projectionJdbcTemplate") NamedParameterJdbcTemplate projectionJdbcTemplate,
            SnowflakeIdGenerator snowflakeIdGenerator,
            ObjectMapper objectMapper,
            FinanceOutboxService financeOutboxService,
            @Qualifier("transactionTemplate") TransactionTemplate transactionTemplate,
            @Qualifier("projectionTransactionTemplate") TransactionTemplate projectionTransactionTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectionJdbcTemplate = projectionJdbcTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
        this.financeOutboxService = financeOutboxService;
        this.transactionTemplate = transactionTemplate;
        this.projectionTransactionTemplate = projectionTransactionTemplate;
    }

    @KafkaListener(topics = "procurement.goods_receipt.posted", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeGoodsReceiptPosted(String payload) throws Exception {
        ProcurementGoodsReceiptPostedEvent event = objectMapper.readValue(payload, ProcurementGoodsReceiptPostedEvent.class);
        validateGoodsReceiptPostedEvent(event);
        if (!transactionTemplate.execute(status ->
                beginIntegrationEvent(event.eventId(), event.eventType(), event.idempotencyKey(), payload))) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> processGoodsReceiptPosted(event));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> markIntegrationFailed(event.eventId(), exception));
            throw exception;
        }
    }

    @KafkaListener(topics = "procurement.supplier.payment.recorded", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeSupplierPaymentRecorded(String payload) throws Exception {
        SupplierPaymentRecordedEvent event = objectMapper.readValue(payload, SupplierPaymentRecordedEvent.class);
        validateSupplierPaymentRecordedEvent(event);
        if (!transactionTemplate.execute(status ->
                beginIntegrationEvent(event.eventId(), event.eventType(), event.idempotencyKey(), payload))) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> processSupplierPaymentRecorded(event, payload));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> markIntegrationFailed(event.eventId(), exception));
            throw exception;
        }
    }

    private void validateSupplierPaymentRecordedEvent(SupplierPaymentRecordedEvent event) {
        requireNonBlank(event.eventId(), "Supplier payment event id is required");
        requireNonBlank(event.eventType(), "Supplier payment event type is required");
        requireNonBlank(event.idempotencyKey(), "Supplier payment idempotency key is required");
        requireNonBlank(event.currencyCode(), "Supplier payment currency code is required");
        requireNonNull(event.paymentId(), "Supplier payment id is required");
        requireNonNull(event.supplierId(), "Supplier id is required");
        requireNonNull(event.paymentTime(), "Supplier payment time is required");
        requirePositive(event.amount(), "Supplier payment amount must be positive");
        BigDecimal allocatedTotal = event.invoiceAllocations().stream()
                .map(allocation -> {
                    requireNonNull(allocation.supplierInvoiceId(), "Supplier payment allocation invoice id is required");
                    requirePositive(allocation.allocatedAmount(), "Supplier payment allocation amount must be positive");
                    return allocation.allocatedAmount();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedTotal.compareTo(event.amount()) > 0) {
            throw new IllegalArgumentException("Supplier payment allocations cannot exceed the payment amount");
        }
    }

    private void validateGoodsReceiptPostedEvent(ProcurementGoodsReceiptPostedEvent event) {
        requireNonBlank(event.eventId(), "Goods receipt event id is required");
        requireNonBlank(event.eventType(), "Goods receipt event type is required");
        requireNonBlank(event.idempotencyKey(), "Goods receipt idempotency key is required");
        requireNonNull(event.goodsReceiptId(), "Goods receipt id is required");
        requireNonNull(event.purchaseOrderId(), "Purchase order id is required");
        requireNonNull(event.regionId(), "Goods receipt region id is required");
        requireNonNull(event.outletId(), "Goods receipt outlet id is required");
        requireNonNull(event.businessDate(), "Goods receipt business date is required");
        requireNonNull(event.postedAt(), "Goods receipt posted time is required");
        if (event.lines().isEmpty()) {
            throw new IllegalArgumentException("Goods receipt lines are required");
        }
        event.lines().forEach(line -> {
            requireNonNull(line.ingredientId(), "Goods receipt ingredient id is required");
            requirePositive(line.qtyReceived(), "Goods receipt quantity received must be positive");
            requirePositive(line.unitCost(), "Goods receipt unit cost must be positive");
        });
    }

    private boolean beginIntegrationEvent(String sourceEventId, String eventType, String idempotencyKey, String payload) {
        lockIntegrationKeys(sourceEventId, eventType, idempotencyKey);
        IntegrationEventRecord existing = findIntegrationEvent(sourceEventId, eventType, idempotencyKey);
        if (existing != null) {
            requireMatchingIntegrationEvent(existing, eventType, idempotencyKey, payload);
            if (sourceEventId.equals(existing.sourceEventId()) && "FAILED".equals(existing.status())) {
                return jdbcTemplate.update("""
                        UPDATE finance.integration_event
                        SET status = 'RECEIVED',
                            payload = CAST(:payload AS jsonb),
                            received_at = CURRENT_TIMESTAMP,
                            processed_at = NULL,
                            error_message = NULL
                        WHERE source_event_id = :sourceEventId
                        """, params(
                        "sourceEventId", existing.sourceEventId(),
                        "payload", payload
                )) == 1;
            }
            return false;
        }
        return jdbcTemplate.update("""
                INSERT INTO finance.integration_event (
                    source_event_id, event_type, idempotency_key, payload, status, received_at
                ) VALUES (
                    :sourceEventId, :eventType, :idempotencyKey, CAST(:payload AS jsonb), 'RECEIVED', CURRENT_TIMESTAMP
                )
                """, params(
                "sourceEventId", sourceEventId,
                "eventType", eventType,
                "idempotencyKey", idempotencyKey,
                "payload", payload
        )) == 1;
    }

    private void markIntegrationProcessed(String sourceEventId) {
        jdbcTemplate.update("""
                UPDATE finance.integration_event
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP, error_message = NULL
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId));
    }

    private void markIntegrationFailed(String sourceEventId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE finance.integration_event
                SET status = 'FAILED',
                    processed_at = NULL,
                    error_message = :errorMessage
                WHERE source_event_id = :sourceEventId
                """, params(
                "sourceEventId", sourceEventId,
                "errorMessage", ExceptionSummaries.safeSummary(exception)
        ));
    }

    private void lockIntegrationKeys(String sourceEventId, String eventType, String idempotencyKey) {
        List<String> lockKeys = new ArrayList<>();
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            lockKeys.add("source:" + sourceEventId);
        }
        if (eventType != null && !eventType.isBlank() && idempotencyKey != null && !idempotencyKey.isBlank()) {
            lockKeys.add("idempotency:" + eventType + ":" + idempotencyKey);
        }
        lockKeys.stream()
                .distinct()
                .sorted()
                .forEach(lockKey -> jdbcTemplate.query(
                        "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                        params("lockKey", lockKey),
                        rs -> null
                ));
    }

    private IntegrationEventRecord findIntegrationEvent(String sourceEventId, String eventType, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT source_event_id, event_type, idempotency_key, payload::text AS payload, status
                FROM finance.integration_event
                WHERE source_event_id = :sourceEventId
                   OR (event_type = :eventType AND idempotency_key = :idempotencyKey)
                ORDER BY received_at, source_event_id
                LIMIT 1
                """, params(
                "sourceEventId", sourceEventId,
                "eventType", eventType,
                "idempotencyKey", idempotencyKey
        ), rs -> rs.next()
                ? new IntegrationEventRecord(
                        rs.getString("source_event_id"),
                        rs.getString("event_type"),
                        rs.getString("idempotency_key"),
                        rs.getString("payload"),
                        rs.getString("status"))
                : null);
    }

    private void requireMatchingIntegrationEvent(
            IntegrationEventRecord existing,
            String eventType,
            String idempotencyKey,
            String payload
    ) {
        if (!Objects.equals(existing.eventType(), eventType)
                || !Objects.equals(existing.idempotencyKey(), idempotencyKey)
                || !jsonEquals(existing.payload(), payload)) {
            throw new IllegalStateException("Finance integration idempotency conflict");
        }
    }

    private void processGoodsReceiptPosted(ProcurementGoodsReceiptPostedEvent event) {
        BigDecimal amount = event.lines().stream()
                .map(line -> line.qtyReceived().multiply(line.unitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long expenseRecordId = insertForId("""
                INSERT INTO finance.expense_record (
                    region_id, outlet_id, employee_id, expense_time, amount, source_type, status, note,
                    submitted_by_user_id, approved_by_user_id, created_at, updated_at, source_event_id,
                    source_reference_type, source_reference_id, posted_at
                ) VALUES (
                    :regionId, :outletId, NULL, :expenseTime, :amount, 'INVENTORY_PURCHASE', 'POSTED', :note,
                    :submittedByUserId, :approvedByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :sourceEventId,
                    'GOODS_RECEIPT', :sourceReferenceId, :postedAt
                )
                """, params(
                "regionId", event.regionId(),
                "outletId", event.outletId(),
                "expenseTime", event.postedAt(),
                "amount", amount,
                "note", "Goods receipt " + event.goodsReceiptId() + " posted from procurement-service",
                "submittedByUserId", event.postedByUserId(),
                "approvedByUserId", event.postedByUserId(),
                "sourceEventId", event.eventId(),
                "sourceReferenceId", event.goodsReceiptId().toString(),
                "postedAt", event.postedAt()
        ));
        jdbcTemplate.update("""
                INSERT INTO finance.expense_inventory_purchase (expense_record_id, goods_receipt_id)
                VALUES (:expenseRecordId, :goodsReceiptId)
                ON CONFLICT (expense_record_id) DO NOTHING
                """, params("expenseRecordId", expenseRecordId, "goodsReceiptId", event.goodsReceiptId()));
        financeOutboxService.emitExpensePosted(
                expenseRecordId,
                event.regionId(),
                event.outletId(),
                null,
                null,
                event.businessDate(),
                "INVENTORY_PURCHASE",
                amount,
                event.correlationId(),
                "GOODS_RECEIPT",
                event.goodsReceiptId().toString()
        );
        markIntegrationProcessed(event.eventId());
    }

    private void processSupplierPaymentRecorded(SupplierPaymentRecordedEvent event, String payload) {
        projectionTransactionTemplate.executeWithoutResult(status -> {
            long postingId = snowflakeIdGenerator.nextId();
            projectionJdbcTemplate.update("""
                    INSERT INTO finance_projection.accounting_posting_projection (
                        posting_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, account_code, debit_amount, credit_amount, currency_code, reference_type, reference_id, payload
                    ) VALUES (
                        :postingId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        NULL, NULL, 'SUPPLIER_PAYMENT', 0, :creditAmount, :currencyCode, 'SUPPLIER_PAYMENT', :referenceId, CAST(:payload AS jsonb)
                    )
                    ON CONFLICT (source_event_id) DO NOTHING
                    """, params(
                    "postingId", postingId,
                    "sourceEventId", event.eventId(),
                    "sourceService", event.sourceService(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "idempotencyKey", event.idempotencyKey(),
                    "creditAmount", event.amount(),
                    "currencyCode", event.currencyCode(),
                    "referenceId", event.paymentId().toString(),
                    "payload", payload
            ));
            projectionJdbcTemplate.update("""
                    INSERT INTO finance_projection.reconciliation_snapshot (
                        snapshot_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, business_date, snapshot_type, snapshot_value, snapshot_payload
                    ) VALUES (
                        :snapshotId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        NULL, NULL, :businessDate, 'SUPPLIER_PAYMENT', :snapshotValue, CAST(:snapshotPayload AS jsonb)
                    )
                    ON CONFLICT (source_event_id) DO NOTHING
                    """, params(
                    "snapshotId", snowflakeIdGenerator.nextId(),
                    "sourceEventId", event.eventId(),
                    "sourceService", event.sourceService(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "idempotencyKey", event.idempotencyKey(),
                    "businessDate", LocalDate.ofInstant(event.paymentTime(), java.time.ZoneOffset.UTC),
                    "snapshotValue", event.amount(),
                    "snapshotPayload", payload
            ));
        });
        markIntegrationProcessed(event.eventId());
    }

    private Long insertForId(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value == null) {
                parameters.addValue((String) values[index], null, Types.NULL);
            } else if (value instanceof Instant instant) {
                parameters.addValue((String) values[index], OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
    }

    private boolean jsonEquals(String left, String right) {
        try {
            var leftNode = objectMapper.readTree(left);
            var rightNode = objectMapper.readTree(right);
            if (leftNode.isObject()) {
                leftNode = leftNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) leftNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            if (rightNode.isObject()) {
                rightNode = rightNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) rightNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to compare integration payload", exception);
        }
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private record IntegrationEventRecord(
            String sourceEventId,
            String eventType,
            String idempotencyKey,
            String payload,
            String status
    ) {
    }
}
