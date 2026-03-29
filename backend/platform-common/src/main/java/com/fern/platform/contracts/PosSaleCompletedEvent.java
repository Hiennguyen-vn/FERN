package com.fern.platform.contracts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PosSaleCompletedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long saleOrderId,
        Long sessionId,
        Long regionId,
        Long outletId,
        LocalDate businessDate,
        Instant completedAt,
        Long completedByUserId,
        Long reservationId,
        List<SalePaymentSnapshot> payments,
        Map<String, Object> saleSnapshot,
        List<RecipeUsageItem> recipeUsageItems
) {
    public static final int CURRENT_VERSION = 1;

    public PosSaleCompletedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        payments = payments == null ? List.of() : List.copyOf(payments);
        saleSnapshot = saleSnapshot == null ? Map.of() : Map.copyOf(saleSnapshot);
        recipeUsageItems = recipeUsageItems == null ? List.of() : List.copyOf(recipeUsageItems);
    }

    public PosSaleCompletedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long saleOrderId,
            Long sessionId,
            Long regionId,
            Long outletId,
            LocalDate businessDate,
            Instant completedAt,
            Long completedByUserId,
            Long reservationId,
            List<SalePaymentSnapshot> payments,
            Map<String, Object> saleSnapshot,
            List<RecipeUsageItem> recipeUsageItems
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                saleOrderId,
                sessionId,
                regionId,
                outletId,
                businessDate,
                completedAt,
                completedByUserId,
                reservationId,
                payments,
                saleSnapshot,
                recipeUsageItems
        );
    }
}
