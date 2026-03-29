package com.fern.iamservice.service;

import jakarta.servlet.http.HttpServletRequest;

public record IamRequestMetadata(String correlationId, String ipAddress, String userAgent) {
    public static IamRequestMetadata from(HttpServletRequest request, String correlationId) {
        if (request == null) {
            return empty();
        }
        return new IamRequestMetadata(
                correlationId,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }

    public static IamRequestMetadata empty() {
        return new IamRequestMetadata(null, null, null);
    }
}
