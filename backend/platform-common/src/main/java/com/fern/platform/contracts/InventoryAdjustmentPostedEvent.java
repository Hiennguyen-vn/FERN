package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryAdjustmentPostedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long stockAdjustmentId,
        Long regionId,
        Long outletId,
        Long ingredientId,
        LocalDate businessDate,
        Instant postedAt,
        Long postedByUserId,
        String adjustmentDirection,
        String reason,
        BigDecimal qtyChange,
        BigDecimal unitCost,
        String sourceReferenceType,
        String sourceReferenceId
) {
    public static final int CURRENT_VERSION = 1;

    public InventoryAdjustmentPostedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
    }

    public InventoryAdjustmentPostedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long stockAdjustmentId,
            Long regionId,
            Long outletId,
            Long ingredientId,
            LocalDate businessDate,
            Instant postedAt,
            Long postedByUserId,
            String adjustmentDirection,
            String reason,
            BigDecimal qtyChange,
            BigDecimal unitCost,
            String sourceReferenceType,
            String sourceReferenceId
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                stockAdjustmentId,
                regionId,
                outletId,
                ingredientId,
                businessDate,
                postedAt,
                postedByUserId,
                adjustmentDirection,
                reason,
                qtyChange,
                unitCost,
                sourceReferenceType,
                sourceReferenceId
        );
    }
}
