package com.fern.posservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * L-05: Custom health indicator that reports the status of the POS service's
 * downstream dependencies (inventory-service and catalog-service).
 *
 * <p>When either circuit breaker is OPEN, the health endpoint reports DOWN,
 * signaling to load balancers and orchestrators that this instance cannot
 * process orders reliably.
 */
@Component
public class DownstreamHealthIndicator implements HealthIndicator {
    private final CircuitBreaker inventoryCircuitBreaker;
    private final CircuitBreaker catalogCircuitBreaker;

    public DownstreamHealthIndicator(
            @Qualifier("inventoryCircuitBreaker") CircuitBreaker inventoryCircuitBreaker,
            @Qualifier("catalogCircuitBreaker") CircuitBreaker catalogCircuitBreaker
    ) {
        this.inventoryCircuitBreaker = inventoryCircuitBreaker;
        this.catalogCircuitBreaker = catalogCircuitBreaker;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        boolean healthy = true;

        String inventoryState = inventoryCircuitBreaker.getState().name();
        builder.withDetail("inventory-circuit-breaker", inventoryState);
        if (inventoryCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            healthy = false;
        }

        String catalogState = catalogCircuitBreaker.getState().name();
        builder.withDetail("catalog-circuit-breaker", catalogState);
        if (catalogCircuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            healthy = false;
        }

        return healthy ? builder.up().build() : builder.down().build();
    }
}
