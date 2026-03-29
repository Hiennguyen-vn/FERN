package com.fern.platform.audit;

import java.time.Instant;
import java.util.Map;

public record SecurityEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        Long userId,
        String outcome,
        String failureReason,
        String ipAddress,
        String userAgent,
        String idempotencyKey,
        Map<String, Object> payload
) {
    public SecurityEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
