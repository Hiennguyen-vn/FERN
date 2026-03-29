package com.fern.platform.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        Long userId,
        Long regionId,
        Long outletId,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        Object oldValue,
        Object newValue,
        String idempotencyKey,
        Map<String, Object> payload
) {
    public AuditEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
