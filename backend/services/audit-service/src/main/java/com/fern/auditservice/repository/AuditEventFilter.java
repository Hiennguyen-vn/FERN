package com.fern.auditservice.repository;

import java.time.Instant;

public record AuditEventFilter(
        Long userId,
        String sourceService,
        String module,
        String action,
        String resourceType,
        String resourceId,
        Instant occurredFrom,
        Instant occurredTo,
        Long regionId,
        Long outletId,
        String outcome,
        String correlationId,
        int limit
) {
}
