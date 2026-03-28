package com.fern.platform.contracts;

import java.time.Instant;
import java.util.Map;

public record OperationalAlertEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        String alertType,
        String severity,
        String summary,
        Long regionId,
        Long outletId,
        String entityType,
        String entityId,
        Map<String, Object> details
) {
    public OperationalAlertEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
