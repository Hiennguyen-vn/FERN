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
        Integer eventVersion,
        String alertType,
        String severity,
        String summary,
        Long regionId,
        Long outletId,
        String entityType,
        String entityId,
        Map<String, Object> details
) {
    public static final int CURRENT_VERSION = 1;

    public OperationalAlertEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public OperationalAlertEvent(
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
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                alertType,
                severity,
                summary,
                regionId,
                outletId,
                entityType,
                entityId,
                details
        );
    }
}
