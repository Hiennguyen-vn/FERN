package com.fern.platform.observability;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class ReactiveCorrelationIdWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);
        String finalCorrelationId = correlationId;

        return chain.filter(exchange.mutate().request(builder -> builder.header(CorrelationId.HEADER, finalCorrelationId)).build())
                .contextWrite(context -> context.put(CorrelationId.MDC_KEY, finalCorrelationId))
                .doFirst(() -> MDC.put(CorrelationId.MDC_KEY, finalCorrelationId))
                .doFinally(signalType -> MDC.remove(CorrelationId.MDC_KEY));
    }
}
