package com.fern.apigateway.security;

import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.SecurityEvent;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernTokenAcceptanceRules;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class GatewaySecurityFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/auth/refresh", "/actuator/health");
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewaySecurityFilter.class);

    private final FernJwtService jwtService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final AuditEventPublisher auditEventPublisher;
    private final Counter authFailureCounter;
    private final Counter outboxEnqueueFailureCounter;

    public GatewaySecurityFilter(
            FernJwtService jwtService,
            ReactiveStringRedisTemplate redisTemplate,
            AuditEventPublisher auditEventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.auditEventPublisher = auditEventPublisher;
        this.authFailureCounter = Counter.builder("fern_auth_failures_total").register(meterRegistry);
        this.outboxEnqueueFailureCounter = Counter.builder("fern_outbox_enqueue_failures_total")
                .tag("source", "api-gateway")
                .tag("event_type", "audit.security")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (PUBLIC_PATHS.contains(path)) {
            return checkRateLimit("gateway:public:" + path + ":" + clientKey(exchange), 10).flatMap(allowed -> {
                if (!allowed) {
                    authFailureCounter.increment();
                    return enqueueSecurityEvent(
                            exchange,
                            null,
                            "gateway.auth.rate_limited",
                            "Rate limit exceeded",
                            Map.of("path", path, "scope", "public")
                    ).then(writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));
                }
                return chain.filter(exchange);
            });
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            authFailureCounter.increment();
            return enqueueSecurityEvent(
                    exchange,
                    null,
                    "gateway.auth.missing_bearer_token",
                    "Missing bearer token",
                    Map.of("path", path)
            ).then(writeError(exchange, HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        }

        FernJwtClaims claims;
        try {
            claims = jwtService.decode(authorization.substring(7));
        } catch (Exception exception) {
            authFailureCounter.increment();
            return enqueueSecurityEvent(
                    exchange,
                    null,
                    "gateway.auth.invalid_bearer_token",
                    "Invalid bearer token",
                    Map.of("path", path)
            ).then(writeError(exchange, HttpStatus.UNAUTHORIZED, "Invalid bearer token"));
        }

        FernPrincipal principal = claims.toPrincipal();
        return isTokenAccepted(claims)
                .flatMap(accepted -> {
                    if (!accepted) {
                        authFailureCounter.increment();
                        return enqueueSecurityEvent(
                                exchange,
                                principal,
                                "gateway.auth.token_rejected",
                                "Token is revoked or stale",
                                Map.of("path", path, "username", principal.username())
                        ).then(writeError(exchange, HttpStatus.UNAUTHORIZED, "Token is revoked or stale"));
                    }
                    return checkRateLimit("gateway:user:" + principal.username(), 120)
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    authFailureCounter.increment();
                                    return enqueueSecurityEvent(
                                            exchange,
                                            principal,
                                            "gateway.auth.rate_limited",
                                            "Rate limit exceeded",
                                            Map.of("path", path, "scope", "user", "username", principal.username())
                                    ).then(writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));
                                }
                                ServerWebExchange mutated = exchange.mutate().request(request -> request
                                                .headers(headers -> {
                                                    String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
                                                    if (correlationId != null && !correlationId.isBlank()) {
                                                        headers.set(CorrelationId.HEADER, correlationId);
                                                    }
                                                    headers.set("X-Fern-User-Id", String.valueOf(principal.userId()));
                                                    headers.set("X-Fern-Username", principal.username());
                                                    headers.set("X-Fern-Roles", String.join(",", principal.roles()));
                                                    headers.set("X-Fern-Permissions", String.join(",", principal.permissions()));
                                                }))
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

    private Mono<Void> enqueueSecurityEvent(
            ServerWebExchange exchange,
            FernPrincipal principal,
            String eventType,
            String failureReason,
            Map<String, Object> payload
    ) {
        SecurityEvent event = new SecurityEvent(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                "api-gateway",
                correlationId(exchange),
                principal == null ? null : principal.userId(),
                "DENIED",
                failureReason,
                clientKey(exchange),
                exchange.getRequest().getHeaders().getFirst("User-Agent"),
                exchange.getRequest().getId(),
                payload
        );
        return Mono.fromRunnable(() -> auditEventPublisher.publishSecurityEvent(event))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(exception -> {
                    outboxEnqueueFailureCounter.increment();
                    LOGGER.warn("gateway_security_event_enqueue_failed requestId={} eventType={}", event.idempotencyKey(), eventType, exception);
                })
                .onErrorResume(exception -> Mono.empty())
                .then();
    }

    private String correlationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return exchange.getRequest().getId();
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
