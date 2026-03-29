package com.fern.auditservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

public record RequestTraceDetailResponse(
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
        String requestId,
        String endpoint,
        String method,
        Integer statusCode,
        Long durationMs,
        Long regionId,
        Long outletId,
        Long userId,
        Object payload,
        boolean detailMasked,
        String detailSummary
) {
}
