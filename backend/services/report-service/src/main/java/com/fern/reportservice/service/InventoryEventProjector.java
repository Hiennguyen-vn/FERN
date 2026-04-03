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
    private static final List<String> INVENTORY_DATASETS = List.of("inventory_movement_fact", "inventory_stock_snapshot");

    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;

    public InventoryEventProjector(ReportIngestionSupport support, DailySummaryProjector dailySummaryProjector) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
    }

    public void ingestAdjustment(String payload, InventoryAdjustmentPostedEvent event) {
        support.ingestWithLanding(
                INVENTORY_DATASETS,
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
                            "movementType", resolveAdjustmentMovementType(event),
                            "qtyChange", event.qtyChange(),
                            "unitCost", event.unitCost(),
                            "payload", payload,
                            "sourceReferenceType", event.sourceReferenceType(),
                            "sourceReferenceId", event.sourceReferenceId()
                    ));
                    support.upsertInventoryStockSnapshot(
                            event.regionId(),
                            event.outletId(),
                            event.ingredientId(),
                            event.qtyChange(),
                            event.unitCost(),
                            null,
                            event.occurredAt()
                    );
                    // Backfill COGS into sales_fact when this event is a SALE_USAGE
                    if ("SALE_ORDER".equals(event.sourceReferenceType())
                            && "SALE_USAGE".equals(event.reason())
                            && event.unitCost() != null
                            && event.qtyChange() != null) {
                        backfillSaleOrderCogs(Long.parseLong(event.sourceReferenceId()), event.unitCost()
                                .multiply(event.qtyChange().abs()));
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

    private void backfillSaleOrderCogs(Long saleOrderId, BigDecimal cogsContribution) {
        support.jdbcTemplate().update("""
                WITH sale_lines AS (
                    SELECT fact_id,
                           line_number,
                           COALESCE(net_amount, 0) AS net_amount,
                           SUM(COALESCE(net_amount, 0)) OVER () AS total_net_amount,
                           ROW_NUMBER() OVER (ORDER BY line_number, fact_id) AS row_num,
                           COUNT(*) OVER () AS line_count
                    FROM report.sales_fact
                    WHERE sale_order_id = :saleOrderId
                ),
                proportional AS (
                    SELECT fact_id,
                           row_num,
                           line_count,
                           total_net_amount,
                           CASE
                               WHEN total_net_amount > 0 THEN ROUND((:cogsContribution * net_amount) / total_net_amount, 2)
                               ELSE 0::numeric
                           END AS rounded_share
                    FROM sale_lines
                ),
                allocation AS (
                    SELECT fact_id,
                           CASE
                               WHEN line_count = 1 THEN :cogsContribution
                               WHEN total_net_amount > 0 AND row_num < line_count THEN rounded_share
                               WHEN total_net_amount > 0 AND row_num = line_count THEN :cogsContribution
                                   - COALESCE(SUM(CASE WHEN row_num < line_count THEN rounded_share ELSE 0 END) OVER (), 0)
                               WHEN row_num = 1 THEN :cogsContribution
                               ELSE 0::numeric
                           END AS allocated_cogs
                    FROM proportional
                )
                UPDATE report.sales_fact sales_fact
                SET cogs_amount = sales_fact.cogs_amount + allocation.allocated_cogs
                FROM allocation
                WHERE sales_fact.fact_id = allocation.fact_id
                """, support.params(
                "saleOrderId", saleOrderId,
                "cogsContribution", cogsContribution
        ));
    }

    private String resolveAdjustmentMovementType(InventoryAdjustmentPostedEvent event) {
        if ("SALE_ORDER".equals(event.sourceReferenceType()) && "SALE_USAGE".equals(event.reason())) {
            return "SALE_USAGE";
        }
        if ("GOODS_RECEIPT_LINE".equals(event.sourceReferenceType()) || "PURCHASE_IN".equals(event.reason())) {
            return "PURCHASE_IN";
        }
        return event.qtyChange().signum() >= 0 ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT";
    }

    public void ingestWaste(String payload, WasteRecordPostedEvent event) {
        support.ingestWithLanding(
                INVENTORY_DATASETS,
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
                    support.upsertInventoryStockSnapshot(
                            event.regionId(),
                            event.outletId(),
                            event.ingredientId(),
                            event.qtyChange(),
                            event.unitCost(),
                            null,
                            event.occurredAt()
                    );
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
                INVENTORY_DATASETS,
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
                        support.upsertInventoryStockSnapshot(
                                event.regionId(),
                                event.outletId(),
                                line.ingredientId(),
                                line.varianceQty(),
                                line.unitCost(),
                                event.businessDate(),
                                event.occurredAt()
                        );
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
