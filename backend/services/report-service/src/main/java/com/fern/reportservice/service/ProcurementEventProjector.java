package com.fern.reportservice.service;

import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Projects goods-receipt-posted events into {@code procurement_fact},
 * while inventory movements/snapshots are sourced from inventory-owned events.
 */
@Component
public class ProcurementEventProjector {
    private static final List<String> PROCUREMENT_DATASETS = List.of("procurement_fact");

    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;

    public ProcurementEventProjector(ReportIngestionSupport support, DailySummaryProjector dailySummaryProjector) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
    }

    public void ingest(String payload, ProcurementGoodsReceiptPostedEvent event) {
        support.ingestWithLanding(
                PROCUREMENT_DATASETS,
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "procurement.goods_receipt.posted",
                payload,
                () -> {
                    BigDecimal totalAmount = BigDecimal.ZERO;
                    for (var line : event.lines()) {
                        BigDecimal lineAmount = line.qtyReceived().multiply(line.unitCost());
                        totalAmount = totalAmount.add(lineAmount);
                    }
                    support.jdbcTemplate().update("""
                            INSERT INTO report.procurement_fact (
                                fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                region_id, outlet_id, goods_receipt_id, purchase_order_id, business_date, fact_amount, fact_type, reference_type, reference_id, payload
                            ) VALUES (
                                :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                :regionId, :outletId, :goodsReceiptId, :purchaseOrderId, :businessDate, :factAmount, :factType, :referenceType, :referenceId, CAST(:payload AS jsonb)
                            )
                            ON CONFLICT DO NOTHING
                            """, support.params(
                            "factId", support.idGenerator().nextId(),
                            "sourceEventId", event.eventId(),
                            "sourceService", event.sourceService(),
                            "eventType", event.eventType(),
                            "occurredAt", event.occurredAt(),
                            "idempotencyKey", event.idempotencyKey(),
                            "regionId", event.regionId(),
                            "outletId", event.outletId(),
                            "goodsReceiptId", event.goodsReceiptId(),
                            "purchaseOrderId", event.purchaseOrderId(),
                            "businessDate", event.businessDate(),
                            "factAmount", totalAmount,
                            "factType", "GOODS_RECEIPT",
                            "referenceType", "GOODS_RECEIPT",
                            "referenceId", String.valueOf(event.goodsReceiptId()),
                            "payload", payload
                    ));
                    dailySummaryProjector.applyDelta(
                            event.eventId(),
                            event.sourceService(),
                            event.eventType(),
                            event.occurredAt(),
                            event.idempotencyKey(),
                            event.regionId(),
                            List.of(event.outletId()),
                            event.businessDate(),
                            payload,
                            new SummaryDelta(BigDecimal.ZERO, totalAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }
}
