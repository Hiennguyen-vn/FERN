package com.fern.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.SecurityEvent;
import com.fern.platform.common.ApiErrorResponse;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaimValidationRules;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernTokenAcceptanceRules;
import com.fern.apigateway.ui.UiContextAttributeKeys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class GatewaySecurityFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of("/auth/login", "/auth/refresh", "/actuator/health");
    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String UI_PATH_PREFIX = "/ui/";
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewaySecurityFilter.class);
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final FernJwtService jwtService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final AuditEventPublisher auditEventPublisher;
    private final Counter authFailureCounter;
    private final Counter outboxEnqueueFailureCounter;
    private final int trustedProxyCount;
    private final String currentServiceName;
    private final String expectedPublicUserIssuer;
    private final GatewayRouteTargetServiceResolver routeTargetServiceResolver;
    private final GatewayUserRelayTokenSupport relayTokenSupport;
    private final ObjectMapper objectMapper;

    public GatewaySecurityFilter(
            FernJwtService jwtService,
            ReactiveStringRedisTemplate redisTemplate,
            AuditEventPublisher auditEventPublisher,
            MeterRegistry meterRegistry,
            FernJwtProperties jwtProperties,
            GatewayRouteTargetServiceResolver routeTargetServiceResolver,
            GatewayUserRelayTokenSupport relayTokenSupport,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${spring.application.name}") String currentServiceName,
            @org.springframework.beans.factory.annotation.Value("${fern.security.trusted-proxy-count:0}") int trustedProxyCount
    ) {
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.auditEventPublisher = auditEventPublisher;
        this.authFailureCounter = Counter.builder("fern_auth_failures_total").register(meterRegistry);
        this.outboxEnqueueFailureCounter = Counter.builder("fern_outbox_enqueue_failures_total")
                .tag("source", "api-gateway")
                .tag("event_type", "audit.security")
                .register(meterRegistry);
        this.trustedProxyCount = trustedProxyCount;
        this.currentServiceName = currentServiceName;
        this.expectedPublicUserIssuer = jwtProperties.getUserTokenIssuer();
        this.routeTargetServiceResolver = routeTargetServiceResolver;
        this.relayTokenSupport = relayTokenSupport;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(INTERNAL_PATH_PREFIX) || path.equals("/internal")) {
            authFailureCounter.increment();
            return enqueueSecurityEvent(
                    exchange,
                    null,
                    "gateway.auth.internal_path_blocked",
                    "Access to internal paths is forbidden",
                    Map.of("path", path)
            ).then(writeError(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "gateway.auth.internal_path_blocked",
                    "Access to internal paths is forbidden",
                    Map.of("path", path)));
        }
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
                    ).then(writeError(
                            exchange,
                            HttpStatus.TOO_MANY_REQUESTS,
                            "gateway.auth.rate_limited",
                            "Rate limit exceeded",
                            Map.of("path", path, "scope", "public")));
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
            ).then(writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "gateway.auth.missing_bearer_token",
                    "Missing bearer token",
                    Map.of("path", path)));
        }

        FernJwtClaims claims;
        try {
            claims = jwtService.decode(authorization.substring(7));
            FernJwtClaimValidationRules.validateGatewayIngress(claims, currentServiceName, expectedPublicUserIssuer);
        } catch (Exception exception) {
            authFailureCounter.increment();
            return enqueueSecurityEvent(
                    exchange,
                    null,
                    "gateway.auth.invalid_bearer_token",
                    "Invalid bearer token",
                    Map.of("path", path)
            ).then(writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "gateway.auth.invalid_bearer_token",
                    "Invalid bearer token",
                    Map.of("path", path)));
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
                        ).then(writeError(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "gateway.auth.token_rejected",
                                "Token is revoked or stale",
                                Map.of("path", path, "username", principal.username())));
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
                                    ).then(writeError(
                                            exchange,
                                            HttpStatus.TOO_MANY_REQUESTS,
                                            "gateway.auth.rate_limited",
                                            "Rate limit exceeded",
                                            Map.of("path", path, "scope", "user", "username", principal.username())));
                                }
                                String forwardedAuthorization = authorization;
                                boolean localUiRequest = path.equals("/ui") || path.startsWith(UI_PATH_PREFIX);
                                if (claims.principalType() == FernPrincipalType.USER && !localUiRequest) {
                                    String targetService = routeTargetServiceResolver.resolve(exchange);
                                    if (targetService == null || targetService.isBlank()) {
                                        authFailureCounter.increment();
                                        return enqueueSecurityEvent(
                                                exchange,
                                                principal,
                                                "gateway.auth.unmapped_downstream_route",
                                                "Unable to determine downstream route target",
                                                Map.of("path", path)
                                        ).then(writeError(
                                                exchange,
                                                HttpStatus.BAD_GATEWAY,
                                                "gateway.auth.unmapped_downstream_route",
                                                "Unable to determine downstream route target",
                                                Map.of("path", path)));
                                    }
                                    forwardedAuthorization = "Bearer " + relayTokenSupport.issueRelayToken(claims, targetService);
                                }
                                String relayAuthorization = forwardedAuthorization;
                                ServerWebExchange mutated = exchange.mutate().request(request -> request
                                                .headers(headers -> {
                                                    String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
                                                    if (correlationId != null && !correlationId.isBlank()) {
                                                        headers.set(CorrelationId.HEADER, correlationId);
                                                    }
                                                    headers.set(HttpHeaders.AUTHORIZATION, relayAuthorization);
                                                    headers.set("X-Fern-User-Id", String.valueOf(principal.userId()));
                                                    headers.set("X-Fern-Username", principal.username());
                                                    headers.set("X-Fern-Roles", String.join(",", principal.roles()));
                                                    headers.set("X-Fern-Permissions", String.join(",", principal.permissions()));
                                                    headers.set("X-Fern-Scope-System", String.valueOf(principal.accessibleScope().system()));
                                                    headers.set("X-Fern-Scope-Regions", principal.accessibleScope().regions().stream()
                                                            .map(String::valueOf)
                                                            .reduce((left, right) -> left + "," + right)
                                                            .orElse(""));
                                                    headers.set("X-Fern-Scope-Outlets", principal.accessibleScope().outlets().stream()
                                                            .map(String::valueOf)
                                                            .reduce((left, right) -> left + "," + right)
                                                            .orElse(""));
                                                }))
                                        .build();
                                mutated.getAttributes().put(UiContextAttributeKeys.USERNAME, principal.username());
                                mutated.getAttributes().put(UiContextAttributeKeys.ROLES, List.copyOf(principal.roles()));
                                mutated.getAttributes().put(UiContextAttributeKeys.PERMISSIONS, List.copyOf(principal.permissions()));
                                mutated.getAttributes().put(UiContextAttributeKeys.SCOPE_SYSTEM, principal.accessibleScope().system());
                                mutated.getAttributes().put(UiContextAttributeKeys.SCOPE_REGIONS, List.copyOf(principal.accessibleScope().regions()));
                                mutated.getAttributes().put(UiContextAttributeKeys.SCOPE_OUTLETS, List.copyOf(principal.accessibleScope().outlets()));
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
                .defaultIfEmpty(0L)
                .onErrorReturn(Long.MAX_VALUE);

        Mono<Long> currentScopeVersion = redisTemplate.opsForValue()
                .get(FernTokenAcceptanceRules.SCOPE_VERSION_KEY)
                .map(Long::parseLong)
                .defaultIfEmpty(0L)
                .onErrorReturn(Long.MAX_VALUE);

        return Mono.zip(blacklisted, currentPolicyVersion, currentScopeVersion)
                .map(tuple -> FernTokenAcceptanceRules.isAccepted(claims, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private Mono<Boolean> checkRateLimit(String key, long limit) {
        return redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), List.of("60"))
                .next()
                .defaultIfEmpty(0L)
                .map(count -> count <= limit)
                .onErrorResume(exception -> {
                    LOGGER.warn("rate_limit_redis_error key={} — failing closed to deny request", key, exception);
                    return Mono.just(false);
                });
    }

    private String clientKey(ServerWebExchange exchange) {
        return GatewayClientIpResolver.resolve(exchange.getRequest(), trustedProxyCount);
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

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiErrorResponse body = new ApiErrorResponse(
                code,
                message,
                Instant.now(),
                correlationId(exchange),
                details == null || details.isEmpty() ? Map.of() : Map.copyOf(details));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException exception) {
            LOGGER.error("gateway_error_serialization_failed code={}", code, exception);
            bytes = "{\"code\":\"internal_error\",\"message\":\"Unable to serialize error response\"}".getBytes();
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
