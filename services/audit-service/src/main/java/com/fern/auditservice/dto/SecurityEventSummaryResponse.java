package com.fern.auditservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

public record SecurityEventSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class)
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
