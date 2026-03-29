package com.fern.platform.contracts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StockCountPostedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long stockCountSessionId,
        Long regionId,
        Long outletId,
        LocalDate businessDate,
        Instant postedAt,
        Long postedByUserId,
        List<StockCountPostedLine> lines
) {
    public static final int CURRENT_VERSION = 1;

    public StockCountPostedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public StockCountPostedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long stockCountSessionId,
            Long regionId,
            Long outletId,
            LocalDate businessDate,
            Instant postedAt,
            Long postedByUserId,
            List<StockCountPostedLine> lines
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                stockCountSessionId,
                regionId,
                outletId,
                businessDate,
                postedAt,
                postedByUserId,
                lines
        );
    }
}
