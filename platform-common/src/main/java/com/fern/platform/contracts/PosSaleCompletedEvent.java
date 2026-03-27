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
    public PosSaleCompletedEvent {
        payments = payments == null ? List.of() : List.copyOf(payments);
        saleSnapshot = saleSnapshot == null ? Map.of() : Map.copyOf(saleSnapshot);
        recipeUsageItems = recipeUsageItems == null ? List.of() : List.copyOf(recipeUsageItems);
    }
}
