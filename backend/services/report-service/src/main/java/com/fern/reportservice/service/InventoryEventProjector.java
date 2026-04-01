package com.fern.reportservice.service;

import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.StockCountPostedEvent;
import com.fern.platform.contracts.StockCountPostedLine;
import com.fern.platform.contracts.WasteRecordPostedEvent;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

// NOTE: SALE_USAGE events (sourceReferenceType = SALE_ORDER, reason = SALE_USAGE) are published
// by inventory-service StockReservationService after committing a reservation. They are ingested
// here via ingestAdjustment(), which backfills sales_fact.cogs_amount for that sale order.

/**
 * Projects inventory events (adjustments, waste records, stock counts)
 * into the {@code inventory_movement_fact} table, then updates daily summaries.
 */
@Component
public class InventoryEventProjector {
    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;

    public InventoryEventProjector(ReportIngestionSupport support, DailySummaryProjector dailySummaryProjector) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
    }

    public void ingestAdjustment(String payload, InventoryAdjustmentPostedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "inventory.adjustment.posted",
                payload,
                () -> {
                    support.jdbcTemplate().update("""
                            INSERT INTO report.inventory_movement_fact (
                                fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                            ) VALUES (
                                :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
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
                            "ingredientId", event.ingredientId(),
                            "businessDate", event.businessDate(),
                            "movementType", event.qtyChange().signum() >= 0 ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT",
                            "qtyChange", event.qtyChange(),
                            "unitCost", event.unitCost(),
                            "payload", payload,
                            "sourceReferenceType", event.sourceReferenceType(),
                            "sourceReferenceId", event.sourceReferenceId()
                    ));
                    // Backfill COGS into sales_fact when this event is a SALE_USAGE
                    if ("SALE_ORDER".equals(event.sourceReferenceType())
                            && "SALE_USAGE".equals(event.reason())
                            && event.unitCost() != null
                            && event.qtyChange() != null) {
                        BigDecimal cogsContribution = event.unitCost()
                                .multiply(event.qtyChange().abs());
                        support.jdbcTemplate().update("""
                                UPDATE report.sales_fact
                                SET cogs_amount = cogs_amount + :cogsContribution
                                WHERE sale_order_id = :saleOrderId
                                """, support.params(
                                "cogsContribution", cogsContribution,
                                "saleOrderId", Long.parseLong(event.sourceReferenceId())
                        ));
                    }
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
                            new SummaryDelta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestWaste(String payload, WasteRecordPostedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "inventory.waste.posted",
                payload,
                () -> {
                    support.jdbcTemplate().update("""
                            INSERT INTO report.inventory_movement_fact (
                                fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                            ) VALUES (
                                :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
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
                            "ingredientId", event.ingredientId(),
                            "businessDate", event.businessDate(),
                            "movementType", "WASTE_OUT",
                            "qtyChange", event.qtyChange(),
                            "unitCost", event.unitCost(),
                            "payload", payload,
                            "sourceReferenceType", event.sourceReferenceType(),
                            "sourceReferenceId", event.sourceReferenceId()
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
                            new SummaryDelta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestStockCount(String payload, StockCountPostedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "inventory.stock_count.posted",
                payload,
                () -> {
                    for (StockCountPostedLine line : event.lines()) {
                        support.jdbcTemplate().update("""
                                INSERT INTO report.inventory_movement_fact (
                                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                    region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                                ) VALUES (
                                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                    :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
                                )
                                ON CONFLICT DO NOTHING
                                """, support.params(
                                "factId", support.idGenerator().nextId(),
                                "sourceEventId", event.eventId(),
                                "sourceService", event.sourceService(),
                                "eventType", event.eventType(),
                                "occurredAt", event.occurredAt(),
                                "idempotencyKey", event.idempotencyKey() + ":line:" + line.ingredientId(),
                                "regionId", event.regionId(),
                                "outletId", event.outletId(),
                                "ingredientId", line.ingredientId(),
                                "businessDate", event.businessDate(),
                                "movementType", line.varianceQty().signum() >= 0 ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT",
                                "qtyChange", line.varianceQty(),
                                "unitCost", line.unitCost(),
                                "payload", support.toJson(line),
                                "sourceReferenceType", "STOCK_COUNT_SESSION",
                                "sourceReferenceId", String.valueOf(event.stockCountSessionId())
                        ));
                    }
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
                            new SummaryDelta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }
}
