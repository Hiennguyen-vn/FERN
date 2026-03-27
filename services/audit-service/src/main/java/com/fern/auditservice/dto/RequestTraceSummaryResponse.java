package com.fern.auditservice.dto;

import java.time.Instant;

public record RequestTraceSummaryResponse(
        Long id,
        String sourceEventId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String correlationId,
        String requestId,
        String endpoint,
        String method,
        Integer statusCode,
        Long durationMs,
        Long regionId,
        Long outletId,
        Long userId,
        String detailSummary
) {
}
