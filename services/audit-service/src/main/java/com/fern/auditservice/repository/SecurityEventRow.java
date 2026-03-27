package com.fern.auditservice.repository;

import java.time.Instant;

public record SecurityEventRow(
        Long id,
        String sourceEventId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String idempotencyKey,
        String correlationId,
        Long userId,
        String outcome,
        String failureReason,
        String ipAddress,
        String userAgent,
        Object payload
) {
}
