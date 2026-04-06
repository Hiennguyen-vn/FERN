package com.fern.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class GatewayClientIpResolverTest {

    @Test
    void shouldIgnoreXForwardedForWhenNoTrustedProxyConfigured() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.5")
                .remoteAddress(new InetSocketAddress("10.0.0.9", 443))
                .build();

        assertThat(GatewayClientIpResolver.resolve(request, 0)).isEqualTo("10.0.0.9");
    }

    @Test
    void shouldResolveClientIpBeforeTrustedProxyChain() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login")
                .header("X-Forwarded-For", "198.51.100.21, 10.0.0.7")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 443))
                .build();

        assertThat(GatewayClientIpResolver.resolve(request, 1)).isEqualTo("198.51.100.21");
    }
}
