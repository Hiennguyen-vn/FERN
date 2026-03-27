package com.fern.auditservice.dto;

import java.time.Instant;

public record SecurityEventSummaryResponse(
        Long id,
        String sourceEventId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredAt,
        Instant ingestedAt,
        String correlationId,
        Long userId,
        String outcome,
        String failureReason,
        String detailSummary
) {
}
