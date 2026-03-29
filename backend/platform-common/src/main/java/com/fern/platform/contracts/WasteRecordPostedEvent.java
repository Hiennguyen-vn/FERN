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
        Integer eventVersion,
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
    public static final int CURRENT_VERSION = 1;

    public WasteRecordPostedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
    }

    public WasteRecordPostedEvent(
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
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                wasteRecordId,
                regionId,
                outletId,
                ingredientId,
                businessDate,
                postedAt,
                postedByUserId,
                reason,
                qtyChange,
                unitCost,
                sourceReferenceType,
                sourceReferenceId
        );
    }
}
