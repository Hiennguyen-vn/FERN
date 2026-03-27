package com.fern.auditservice.dto;

import java.time.Instant;

public record SecurityEventDetailResponse(
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
        Object payload,
        boolean detailMasked,
        String detailSummary
) {
}
