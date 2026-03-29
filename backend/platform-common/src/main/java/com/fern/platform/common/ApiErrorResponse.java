package com.fern.platform.common;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String correlationId,
        Map<String, Object> details
) {
}
