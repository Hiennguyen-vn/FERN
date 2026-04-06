package com.fern.apigateway.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.server.reactive.ServerHttpRequest;

final class GatewayClientIpResolver {
    private GatewayClientIpResolver() {
    }

    /**
     * Resolves client IP from X-Forwarded-For based on trusted proxy count.
     *
     * <p>When trustedProxyCount is 0, X-Forwarded-For is ignored to prevent spoofing.
     * When trustedProxyCount is greater than 0, the resolver picks the IP just before
     * the trusted proxy chain (left to right in XFF).
     */
    static String resolve(ServerHttpRequest request, int trustedProxyCount) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (trustedProxyCount > 0 && xForwardedFor != null && !xForwardedFor.isBlank()) {
            List<String> addresses = Arrays.stream(xForwardedFor.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (!addresses.isEmpty()) {
                int clientIndex = Math.max(0, addresses.size() - trustedProxyCount - 1);
                return addresses.get(clientIndex);
            }
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
