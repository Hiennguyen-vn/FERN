package com.fern.procurementservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ProcurementEventPublisher {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ProcurementJdbcRepository procurementJdbcRepository;
    private final Clock clock;

    ProcurementEventPublisher(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ProcurementJdbcRepository procurementJdbcRepository,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.procurementJdbcRepository = procurementJdbcRepository;
        this.clock = clock;
    }

    void enqueueGoodsReceiptPostedEvent(GoodsReceiptRecord record, FernPrincipal principal, String correlationId) {
        var goodsReceipt = procurementJdbcRepository.mapGoodsReceipt(record);
        var lines = goodsReceipt.lines().stream()
                .map(line -> new GoodsReceiptPostedLine(line.ingredientId(), line.qtyReceived(), line.unitCost(), line.id()))
                .toList();
        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                UUID.randomUUID().toString(),
                "procurement.goods_receipt.posted",
                Instant.now(clock),
                "procurement-service",
                correlationId,
                UUID.randomUUID().toString(),
                record.id(),
                record.purchaseOrderId(),
                record.regionId(),
                record.outletId(),
                record.businessDate(),
                Instant.now(clock),
                principal.userId(),
                lines
        );
        enqueueOutbox("GOODS_RECEIPT", record.id().toString(), "procurement.goods_receipt.posted", record.outletId().toString(), event);
    }

    void enqueueSupplierPaymentRecordedEvent(Long supplierPaymentId, FernPrincipal principal, String correlationId) {
        SupplierPaymentResponse payment = procurementJdbcRepository.getSupplierPayment(supplierPaymentId);
        SupplierPaymentRecordedEvent event = new SupplierPaymentRecordedEvent(
                UUID.randomUUID().toString(),
                "procurement.supplier.payment.recorded",
                Instant.now(clock),
                "procurement-service",
                correlationId,
                UUID.randomUUID().toString(),
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
        jdbcTemplate.update("""
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
                "payload", procurementJdbcRepository.toJson(payload)
        ));
    }
}
