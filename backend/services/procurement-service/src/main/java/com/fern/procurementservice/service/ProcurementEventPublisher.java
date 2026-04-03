package com.fern.procurementservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ProcurementEventPublisher {
    private final ProcurementJdbcRepository procurementJdbcRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ProcurementEventPublisher(
            ProcurementJdbcRepository procurementJdbcRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.procurementJdbcRepository = procurementJdbcRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void enqueueGoodsReceiptPostedEvent(GoodsReceiptRecord record, FernPrincipal principal, String correlationId) {
        GoodsReceiptRecord effectiveRecord = effectiveGoodsReceiptRecord(record);
        var goodsReceipt = procurementJdbcRepository.mapGoodsReceipt(effectiveRecord);
        var lines = goodsReceipt.lines().stream()
                .map(line -> new GoodsReceiptPostedLine(line.ingredientId(), line.qtyReceived(), line.unitCost(), line.id()))
                .toList();
        Instant postedAt = effectiveRecord.postedAt() == null ? Instant.now(clock) : effectiveRecord.postedAt();
        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                UUID.randomUUID().toString(),
                "procurement.goods_receipt.posted",
                postedAt,
                "procurement-service",
                correlationId,
                goodsReceiptPostedIdempotencyKey(effectiveRecord.id()),
                effectiveRecord.id(),
                effectiveRecord.purchaseOrderId(),
                effectiveRecord.regionId(),
                effectiveRecord.outletId(),
                effectiveRecord.businessDate(),
                postedAt,
                principal.userId(),
                lines
        );
        enqueueOutbox(
                "GOODS_RECEIPT",
                effectiveRecord.id().toString(),
                "procurement.goods_receipt.posted",
                effectiveRecord.outletId().toString(),
                event
        );
    }

    void enqueueSupplierPaymentRecordedEvent(Long supplierPaymentId, FernPrincipal principal, String correlationId) {
        SupplierPaymentResponse payment = procurementJdbcRepository.getSupplierPayment(supplierPaymentId);
        SupplierPaymentRecordedEvent event = new SupplierPaymentRecordedEvent(
                UUID.randomUUID().toString(),
                "procurement.supplier.payment.recorded",
                payment.paymentTime(),
                "procurement-service",
                correlationId,
                supplierPaymentRecordedIdempotencyKey(payment.id()),
                payment.id(),
                payment.supplierId(),
                payment.paymentTime(),
                payment.amount(),
                payment.currencyCode(),
                payment.invoiceAllocations().stream()
                        .map(allocation -> new SupplierPaymentAllocation(allocation.supplierInvoiceId(), allocation.allocatedAmount()))
                        .toList(),
                principal.userId()
        );
        enqueueOutbox("SUPPLIER_PAYMENT", payment.id().toString(), "procurement.supplier.payment.recorded", payment.supplierId().toString(), event);
    }

    private void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        String payloadJson = procurementJdbcRepository.toJson(payload);
        lockOutboxKey(aggregateType, aggregateId, eventType);
        OutboxEventRecord existing = findOutboxEvent(aggregateType, aggregateId, eventType);
        if (existing != null) {
            requireMatchingOutboxPayload(existing, partitionKey, payloadJson);
            if ("FAILED".equals(existing.status())) {
                reviveFailedOutboxEvent(existing.id());
            }
            return;
        }
        procurementJdbcRepository.jdbcTemplate().update("""
                INSERT INTO procurement.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', CURRENT_TIMESTAMP
                )
                """, procurementJdbcRepository.params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "partitionKey", partitionKey,
                "payload", payloadJson
        ));
    }

    private void lockOutboxKey(String aggregateType, String aggregateId, String eventType) {
        String lockKey = aggregateType + ":" + aggregateId + ":" + eventType;
        procurementJdbcRepository.jdbcTemplate().query(
                "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                procurementJdbcRepository.params("lockKey", lockKey),
                rs -> null
        );
    }

    private OutboxEventRecord findOutboxEvent(String aggregateType, String aggregateId, String eventType) {
        return procurementJdbcRepository.jdbcTemplate().query("""
                SELECT id::text AS id,
                       aggregate_type,
                       aggregate_id,
                       event_type,
                       partition_key,
                       payload::text AS payload,
                       status
                FROM procurement.outbox_event
                WHERE aggregate_type = :aggregateType
                  AND aggregate_id = :aggregateId
                  AND event_type = :eventType
                ORDER BY created_at, id
                LIMIT 1
                """, procurementJdbcRepository.params(
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType
        ), rs -> rs.next()
                ? new OutboxEventRecord(
                        rs.getString("id"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("partition_key"),
                        rs.getString("payload"),
                        rs.getString("status"))
                : null);
    }

    private void reviveFailedOutboxEvent(String id) {
        procurementJdbcRepository.jdbcTemplate().update("""
                UPDATE procurement.outbox_event
                SET status = 'PENDING',
                    retry_count = 0,
                    last_attempt_at = NULL,
                    last_error = NULL,
                    published_at = NULL
                WHERE id = CAST(:id AS uuid)
                """, procurementJdbcRepository.params("id", id));
    }

    private void requireMatchingOutboxPayload(OutboxEventRecord existing, String partitionKey, String payloadJson) {
        if (!Objects.equals(existing.partitionKey(), partitionKey)) {
            throw new IllegalStateException("Procurement outbox idempotency conflict");
        }
        if (!jsonEqualsIgnoringEnvelope(existing.payload(), payloadJson)) {
            throw new IllegalStateException("Procurement outbox idempotency conflict");
        }
    }

    private GoodsReceiptRecord effectiveGoodsReceiptRecord(GoodsReceiptRecord record) {
        if (record.postedAt() != null) {
            return record;
        }
        GoodsReceiptRecord persistedRecord = procurementJdbcRepository.requireGoodsReceipt(record.id());
        return persistedRecord.postedAt() == null ? record : persistedRecord;
    }

    private boolean jsonEqualsIgnoringEnvelope(String left, String right) {
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
            return Objects.equals(leftNode, rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to compare procurement outbox payload", exception);
        }
    }

    private String goodsReceiptPostedIdempotencyKey(Long goodsReceiptId) {
        return "procurement.goods_receipt.posted:receipt:" + goodsReceiptId;
    }

    private String supplierPaymentRecordedIdempotencyKey(Long supplierPaymentId) {
        return "procurement.supplier.payment.recorded:payment:" + supplierPaymentId;
    }

    private record OutboxEventRecord(
            String id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String partitionKey,
            String payload,
            String status
    ) {
    }
}
