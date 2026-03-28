package com.fern.apigateway.security;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernTokenAcceptanceRules;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewaySecurityFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/auth/refresh", "/actuator/health");

    private final FernJwtService jwtService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Counter authFailureCounter;

    public GatewaySecurityFilter(FernJwtService jwtService, ReactiveStringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.authFailureCounter = Counter.builder("fern_auth_failures_total").register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_PATHS.contains(path)) {
            return checkRateLimit("gateway:public:" + path + ":" + clientKey(exchange), 10).flatMap(allowed -> {
                if (!allowed) {
                    authFailureCounter.increment();
                    return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                }
                return chain.filter(exchange);
            });
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            authFailureCounter.increment();
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        FernJwtClaims claims;
        try {
            claims = jwtService.decode(authorization.substring(7));
        } catch (Exception exception) {
            authFailureCounter.increment();
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }

        FernPrincipal principal = claims.toPrincipal();
        return isTokenAccepted(claims)
                .flatMap(accepted -> {
                    if (!accepted) {
                        authFailureCounter.increment();
                        return writeError(exchange, HttpStatus.UNAUTHORIZED, "Token is revoked or stale");
                    }
                    return checkRateLimit("gateway:user:" + principal.username(), 120)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    authFailureCounter.increment();
                                    return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                                }
                                ServerWebExchange mutated = exchange.mutate().request(request -> request
                                                .header(CorrelationId.HEADER, exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER))
                                                .header("X-Fern-User-Id", String.valueOf(principal.userId()))
                                                .header("X-Fern-Username", principal.username())
                                                .header("X-Fern-Roles", String.join(",", principal.roles()))
                                                .header("X-Fern-Permissions", String.join(",", principal.permissions())))
                                        .build();
                                return chain.filter(mutated);
                            });
                });
    }

    private Mono<Boolean> isTokenAccepted(FernJwtClaims claims) {
        Mono<Boolean> blacklisted = redisTemplate.opsForValue()
                .get(FernTokenAcceptanceRules.BLACKLIST_PREFIX + claims.jti())
                .map(value -> true)
                .defaultIfEmpty(false);

        Mono<Long> currentPolicyVersion = redisTemplate.opsForValue()
                .get(FernTokenAcceptanceRules.POLICY_VERSION_KEY)
                .map(Long::parseLong)
                .defaultIfEmpty(claims.policyVersion());

        Mono<Long> currentScopeVersion = redisTemplate.opsForValue()
                .get(FernTokenAcceptanceRules.SCOPE_VERSION_KEY)
                .map(Long::parseLong)
                .defaultIfEmpty(claims.scopeVersion());

        return Mono.zip(blacklisted, currentPolicyVersion, currentScopeVersion)
                .map(tuple -> FernTokenAcceptanceRules.isAccepted(claims, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private Mono<Boolean> checkRateLimit(String key, long limit) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> expire = count == 1
                            ? redisTemplate.expire(key, Duration.ofMinutes(1))
                            : Mono.just(Boolean.TRUE);
                    return expire.thenReturn(count <= limit);
                });
    }

    private String clientKey(ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() == null || exchange.getRequest().getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes();
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
