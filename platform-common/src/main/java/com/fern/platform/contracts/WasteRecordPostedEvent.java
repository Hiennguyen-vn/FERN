package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WasteRecordPostedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Long wasteRecordId,
        Long regionId,
        Long outletId,
        Long ingredientId,
        LocalDate businessDate,
        Instant postedAt,
        Long postedByUserId,
        String reason,
        BigDecimal qtyChange,
        BigDecimal unitCost,
        String sourceReferenceType,
        String sourceReferenceId
) {
}
