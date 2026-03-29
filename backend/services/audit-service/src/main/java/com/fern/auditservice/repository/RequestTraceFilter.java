package com.fern.auditservice.repository;

import java.time.Instant;

public record RequestTraceFilter(
        Long userId,
        String sourceService,
        String module,
        String endpoint,
        String method,
        Instant occurredFrom,
        Instant occurredTo,
        Long regionId,
        Long outletId,
        Integer statusCode,
        String correlationId,
        int limit
) {
}
