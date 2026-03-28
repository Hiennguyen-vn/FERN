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
        Long stockCountSessionId,
        Long regionId,
        Long outletId,
        LocalDate businessDate,
        Instant postedAt,
        Long postedByUserId,
        List<StockCountPostedLine> lines
) {
    public StockCountPostedEvent {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
