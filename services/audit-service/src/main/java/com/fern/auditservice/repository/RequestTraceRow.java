package com.fern.auditservice.repository;

import java.time.Instant;

public record RequestTraceRow(
        Long id,
        String sourceEventId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String idempotencyKey,
        String correlationId,
        String requestId,
        String endpoint,
        String method,
        Integer statusCode,
        Long durationMs,
        Long regionId,
        Long outletId,
        Long userId,
        Object payload
) {
}
