package com.fern.apigateway.security;

import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.audit.SensitiveDataMasker;
import com.fern.platform.observability.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class GatewayLoggingFilter implements WebFilter, Ordered {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayLoggingFilter.class);
    private final AuditEventPublisher auditEventPublisher;
    private final Counter outboxEnqueueFailureCounter;

    public GatewayLoggingFilter(AuditEventPublisher auditEventPublisher, MeterRegistry meterRegistry) {
        this.auditEventPublisher = auditEventPublisher;
        this.outboxEnqueueFailureCounter = Counter.builder("fern_outbox_enqueue_failures_total")
                .tag("source", "api-gateway")
                .tag("event_type", "request.trace")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Instant startedAt = Instant.now();
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                    LOGGER.info(
                            "gateway_request method={} path={} status={} durationMs={}",
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getPath(),
                            exchange.getResponse().getStatusCode(),
                            durationMs
                    );
                    publishTrace(exchange, startedAt, durationMs);
                });
    }

    private void publishTrace(ServerWebExchange exchange, Instant startedAt, long durationMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", exchange.getRequest().getQueryParams().toSingleValueMap());
        payload.put("remoteAddress", exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().toString());
        payload.put("userAgent", exchange.getRequest().getHeaders().getFirst("User-Agent"));
        RequestTraceEvent event = new RequestTraceEvent(
                UUID.randomUUID().toString(),
                "request.trace.recorded",
                startedAt,
                "api-gateway",
                correlationId(exchange),
                exchange.getRequest().getId(),
                exchange.getRequest().getPath().value(),
                exchange.getRequest().getMethod() == null ? null : exchange.getRequest().getMethod().name(),
                exchange.getResponse().getStatusCode() == null ? null : exchange.getResponse().getStatusCode().value(),
                durationMs,
                parseLongHeader(exchange, "X-Fern-User-Id"),
                null,
                null,
                exchange.getRequest().getId(),
                castToMap(SensitiveDataMasker.mask(payload))
        );
        Mono.fromRunnable(() -> auditEventPublisher.publishRequestTrace(event))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(exception -> {
                    outboxEnqueueFailureCounter.increment();
                    LOGGER.warn("gateway_request_trace_enqueue_failed requestId={}", event.requestId(), exception);
                })
                .onErrorResume(exception -> Mono.empty())
                .subscribe();
    }

    private String correlationId(ServerWebExchange exchange) {
        String responseHeader = exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER);
        if (responseHeader != null && !responseHeader.isBlank()) {
            return responseHeader;
        }
        return exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Long parseLongHeader(ServerWebExchange exchange, String headerName) {
        String value = exchange.getRequest().getHeaders().getFirst(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
