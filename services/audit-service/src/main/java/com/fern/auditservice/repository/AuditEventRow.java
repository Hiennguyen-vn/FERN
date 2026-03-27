package com.fern.auditservice.repository;

import java.time.Instant;

public record AuditEventRow(
        Long id,
        String sourceEventId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String idempotencyKey,
        String correlationId,
        Long regionId,
        Long outletId,
        Long userId,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        Object oldValue,
        Object newValue,
        Object payload
) {
}
