package com.fern.auditservice.repository;

import java.time.Instant;

public record SecurityEventFilter(
        Long userId,
        String sourceService,
        String module,
        String eventType,
        Instant occurredFrom,
        Instant occurredTo,
        String outcome,
        String correlationId,
        int limit
) {
}
