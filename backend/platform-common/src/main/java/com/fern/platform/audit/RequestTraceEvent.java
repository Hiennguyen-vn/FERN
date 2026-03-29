package com.fern.platform.audit;

import java.time.Instant;
import java.util.Map;

public record RequestTraceEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String requestId,
        String endpoint,
        String method,
        Integer statusCode,
        Long durationMs,
        Long userId,
        Long regionId,
        Long outletId,
        String idempotencyKey,
        Map<String, Object> payload
) {
    public RequestTraceEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
