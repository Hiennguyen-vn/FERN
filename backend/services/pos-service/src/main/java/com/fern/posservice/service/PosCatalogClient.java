package com.fern.posservice.service;

import com.fern.platform.common.ResourceNotFoundException;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PosCatalogClient {
    private static final ParameterizedTypeReference<List<RecipeSnapshot>> RECIPE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;
    private final Cache<String, MenuResponse> menuCache;

    public PosCatalogClient(
            @Qualifier("catalogRestClient") RestClient restClient,
            @Qualifier("catalogCircuitBreaker") CircuitBreaker catalogCircuitBreaker,
            @Qualifier("catalogClientSpec") FernDownstreamClientSpec clientSpec,
            FernServiceTokenSupport serviceTokenSupport,
            FernDownstreamClientFactory downstreamClientFactory,
            FernDownstreamErrorMapper errorMapper,
            @Value("${fern.pos.menu-cache-ttl-seconds:15}") int menuCacheTtlSeconds
    ) {
        this.restClient = restClient;
        this.circuitBreaker = catalogCircuitBreaker;
        this.clientSpec = clientSpec;
        this.serviceTokenSupport = serviceTokenSupport;
        this.downstreamClientFactory = downstreamClientFactory;
        this.errorMapper = errorMapper;
        // P1-04 FIX: Reduced default TTL from 60s to 15s to limit the window
        // during which stale prices can cause incorrect order totals.
        this.menuCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(menuCacheTtlSeconds))
                .maximumSize(200)
                .build();
    }

    /**
     * Invalidates the menu cache for a specific outlet and business date.
     * Call when a price change event is received from catalog-service.
     */
    public void invalidateMenuCache(Long outletId, LocalDate businessDate) {
        String cacheKey = outletId + ":" + businessDate;
        menuCache.invalidate(cacheKey);
    }

    /**
     * Invalidates all cached menus for a specific outlet (all business dates).
     * Use for a full outlet price refresh.
     */
    public void invalidateMenuCacheForOutlet(Long outletId) {
        String prefix = outletId + ":";
        menuCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Invalidates the entire menu cache.
     * Useful for admin-triggered global refresh.
     */
    public void invalidateAllMenuCaches() {
        menuCache.invalidateAll();
    }

    public MenuResponse fetchMenu(FernPrincipal principal, Long outletId, LocalDate businessDate) {
        String cacheKey = outletId + ":" + businessDate;
        MenuResponse cached = menuCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        MenuResponse response = execute(() -> Objects.requireNonNull(restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/catalog/menu")
                        .queryParam("outletId", outletId)
                        .queryParam("at", businessDate)
                        .build())
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                PosServiceNames.POS_SERVICE,
                                PosServiceNames.CATALOG_SERVICE,
                                Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)
                        ),
                        principal,
                        MDC.get(CorrelationId.MDC_KEY)
                )::contribute)
                .retrieve()
                .body(MenuResponse.class)));
        menuCache.put(cacheKey, response);
        return response;
    }

    public List<RecipeSnapshot> resolveRecipes(FernPrincipal principal, List<Long> productIds, LocalDate businessDate) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        String joinedIds = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return execute(() -> Objects.requireNonNull(restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/catalog/recipe-resolutions")
                        .queryParam("productIds", joinedIds)
                        .queryParam("at", businessDate)
                        .build())
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                PosServiceNames.POS_SERVICE,
                                PosServiceNames.CATALOG_SERVICE,
                                Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)
                        ),
                        principal,
                        MDC.get(CorrelationId.MDC_KEY)
                )::contribute)
                .retrieve()
                .body(RECIPE_LIST_TYPE)));
    }

    /**
     * Resolves an applicable promotion/discount for a given code, outlet, and order total.
     * Returns null if no applicable promotion is found (404 from catalog-service).
     */
    public PromotionSnapshot resolvePromotion(
            FernPrincipal principal,
            String code,
            Long outletId,
            Long regionId,
            BigDecimal orderTotal,
            LocalDate businessDate
    ) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return execute(() -> Objects.requireNonNull(restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/catalog/promotion-resolution")
                            .queryParam("code", code)
                            .queryParam("outletId", outletId)
                            .queryParam("regionId", regionId)
                            .queryParam("orderTotal", orderTotal)
                            .queryParam("at", businessDate)
                            .build())
                    .headers(FernDownstreamHeadersContributor.bearerToken(
                            serviceTokenSupport.issueToken(
                                    PosServiceNames.POS_SERVICE,
                                    PosServiceNames.CATALOG_SERVICE,
                                    Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)
                            ),
                            principal,
                            MDC.get(CorrelationId.MDC_KEY)
                    )::contribute)
                    .retrieve()
                    .body(PromotionSnapshot.class)));
        } catch (ResourceNotFoundException exception) {
            // P1-02 FIX: Only treat 404 (promotion not found) as "no discount".
            // Other errors (network, 500, circuit-breaker) must propagate so the
            // caller knows the promotion couldn't be validated — preventing silent
            // price miscalculation during catalog-service outages.
            return null;
        }
    }

    public record PromotionSnapshot(
            Long id,
            String code,
            String name,
            String promotionType,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            String scopeType,
            Long scopeId
    ) {
    }

    private <T> T execute(Supplier<T> supplier) {
        return downstreamClientFactory.execute(operation("catalog"), circuitBreaker, supplier, errorMapper);
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }
}
