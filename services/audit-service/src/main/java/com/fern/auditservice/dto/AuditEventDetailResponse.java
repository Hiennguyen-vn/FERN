package com.fern.auditservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

public record AuditEventDetailResponse(
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
        Long regionId,
        Long outletId,
        Long userId,
        String action,
        String resourceType,
        String resourceId,
        String outcome,
        Object oldValue,
        Object newValue,
        Object payload,
        boolean detailMasked,
        String detailSummary
) {
}
