package com.fern.apigateway.security;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * L-04: Gateway-level rate limiter using Redis sliding window counters.
 *
 * <p>Applies per-IP rate limits to high-volume write endpoints to prevent DoS attacks.
 * This supplements the auth-specific rate limiter in iam-service by protecting
 * all downstream services.
 *
 * <h3>Rate Limits</h3>
 * <ul>
 *     <li>POS order creation ({@code POST /sale-orders}) -- 60 req/min per IP</li>
 *     <li>POS payment ({@code POST /sale-orders/{id}/payments}) -- 60 req/min per IP</li>
 *     <li>General API (all other routes) -- 200 req/min per IP</li>
 * </ul>
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitFilter.class);

    private static final long POS_WRITE_LIMIT = 60;
    private static final long GENERAL_LIMIT = 200;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int trustedProxyCount;

    public GatewayRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Value("${fern.security.trusted-proxy-count:0}") int trustedProxyCount
    ) {
        this.redisTemplate = redisTemplate;
        this.trustedProxyCount = trustedProxyCount;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "GET";

        long limit = resolveLimit(method, path);
        if (limit <= 0) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(request);
        String bucketKey = "gw:rate:" + bucketPrefix(method, path) + ":" + clientIp;

        return redisTemplate.execute(
                INCR_SCRIPT,
                List.of(bucketKey),
                List.of(String.valueOf(WINDOW.toMillis()))
        )
        .next()
        .defaultIfEmpty(1L)
        .flatMap(count -> {
            if (count > limit) {
                log.warn("RATE_LIMIT_EXCEEDED ip={} path={} count={} limit={}", clientIp, path, count, limit);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(WINDOW.toSeconds()));
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        });
    }

    @Override
    public int getOrder() {
        // Run before the security filter but after logging
        return -10;
    }

    private long resolveLimit(String method, String path) {
        if ("POST".equals(method)) {
            if (path.startsWith("/sale-orders")) {
                return POS_WRITE_LIMIT;
            }
        }
        return GENERAL_LIMIT;
    }

    private String bucketPrefix(String method, String path) {
        if ("POST".equals(method) && path.startsWith("/sale-orders")) {
            return "pos-write";
        }
        return "general";
    }

    /**
     * Resolves client IP using trusted proxy policy.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        return GatewayClientIpResolver.resolve(request, trustedProxyCount);
    }
}
