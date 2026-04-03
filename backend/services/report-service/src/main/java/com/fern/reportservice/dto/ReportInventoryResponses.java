package com.fern.reportservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ReportInventoryResponses {
    private ReportInventoryResponses() {
    }

    public record InventoryStockBalanceSnapshotResponse(
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qtyOnHand,
            BigDecimal unitCost,
            LocalDate lastCountDate,
            Instant lastMovementAt
    ) {
    }

    public record InventoryMovementFactResponse(
            String sourceEventId,
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qtyChange,
            LocalDate businessDate,
            Instant occurredAt,
            String movementType,
            BigDecimal unitCost,
            String sourceReferenceType,
            String sourceReferenceId
    ) {
    }

    public record ProjectionFreshnessResponse(
            String dataset,
            Instant lastOccurredAt,
            Instant lastIngestedAt,
            long lagMillis,
            long failedLandingCount
    ) {
    }
}
