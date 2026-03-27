package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PosCatalogClient {
    private static final ParameterizedTypeReference<List<RecipeSnapshot>> RECIPE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final PosInternalClientSupport internalClientSupport;
    private final PosDownstreamErrorHandler errorHandler;

    public PosCatalogClient(
            @Qualifier("catalogRestClient") RestClient restClient,
            CircuitBreaker catalogCircuitBreaker,
            PosInternalClientSupport internalClientSupport,
            PosDownstreamErrorHandler errorHandler
    ) {
        this.restClient = restClient;
        this.circuitBreaker = catalogCircuitBreaker;
        this.internalClientSupport = internalClientSupport;
        this.errorHandler = errorHandler;
    }

    public MenuResponse fetchMenu(FernPrincipal principal, Long outletId, LocalDate businessDate) {
        return execute(() -> Objects.requireNonNull(restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/catalog/menu")
                        .queryParam("outletId", outletId)
                        .queryParam("at", businessDate)
                        .build())
                .headers(headers -> internalClientSupport.applyInternalHeaders(
                        headers,
                        principal,
                        Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)
                ))
                .retrieve()
                .body(MenuResponse.class)));
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
                .headers(headers -> internalClientSupport.applyInternalHeaders(
                        headers,
                        principal,
                        Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)
                ))
                .retrieve()
                .body(RECIPE_LIST_TYPE)));
    }

    private <T> T execute(Supplier<T> supplier) {
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException exception) {
            throw errorHandler.serviceUnavailable(PosServiceNames.CATALOG_SERVICE, exception);
        } catch (RestClientResponseException exception) {
            throw errorHandler.translateResponse(PosServiceNames.CATALOG_SERVICE, exception);
        } catch (RestClientException exception) {
            throw errorHandler.serviceUnavailable(PosServiceNames.CATALOG_SERVICE, exception);
        }
    }
}
