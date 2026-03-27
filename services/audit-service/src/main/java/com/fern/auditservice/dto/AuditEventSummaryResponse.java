package com.fern.auditservice.dto;

import java.time.Instant;

public record AuditEventSummaryResponse(
        Long id,
        String sourceEventId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String correlationId,
        Long regionId,
        Long outletId,
        Long userId,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        String detailSummary
) {
}
