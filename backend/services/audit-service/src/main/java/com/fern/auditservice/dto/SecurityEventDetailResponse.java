package com.fern.auditservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

public record SecurityEventDetailResponse(
        @JsonSerialize(using = ToStringSerializer.class)
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
