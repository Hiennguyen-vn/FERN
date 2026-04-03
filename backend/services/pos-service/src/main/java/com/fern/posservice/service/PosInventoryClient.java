package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PosInventoryClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public PosInventoryClient(
            @Qualifier("inventoryRestClient") RestClient restClient,
            @Qualifier("inventoryCircuitBreaker") CircuitBreaker inventoryCircuitBreaker,
            @Qualifier("inventoryClientSpec") FernDownstreamClientSpec clientSpec,
            FernServiceTokenSupport serviceTokenSupport,
            FernDownstreamClientFactory downstreamClientFactory,
            FernDownstreamErrorMapper errorMapper
    ) {
        this.restClient = restClient;
        this.circuitBreaker = inventoryCircuitBreaker;
        this.clientSpec = clientSpec;
        this.serviceTokenSupport = serviceTokenSupport;
        this.downstreamClientFactory = downstreamClientFactory;
        this.errorMapper = errorMapper;
    }

    public SaleReservationResponse reserveInventory(
            FernPrincipal principal,
            Long outletId,
            LocalDate businessDate,
            Long saleOrderId,
            List<RecipeUsageItem> usageItems
    ) {
        return execute(() -> Objects.requireNonNull(restClient.post()
                .uri("/internal/inventory/sale-reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                PosServiceNames.POS_SERVICE,
                                PosServiceNames.INVENTORY_SERVICE,
                                Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)
                        ),
                        principal,
                        MDC.get(CorrelationId.MDC_KEY)
                )::contribute)
                .body(new SaleReservationRequest(
                        outletId,
                        businessDate,
                        saleOrderId,
                        usageItems.stream()
                                .map(item -> new SaleReservationItem(
                                        item.ingredientId(),
                                        item.ingredientCode(),
                                        item.ingredientName(),
                                        item.uomCode(),
                                        item.qty()
                                ))
                                .toList()
                ))
                .retrieve()
                .body(SaleReservationResponse.class)));
    }

    public void releaseInventoryReservation(FernPrincipal principal, Long reservationId) {
        execute(() -> {
            restClient.post()
                    .uri("/internal/inventory/sale-reservations/{reservationId}/cancel", reservationId)
                    .headers(FernDownstreamHeadersContributor.bearerToken(
                            serviceTokenSupport.issueToken(
                                    PosServiceNames.POS_SERVICE,
                                    PosServiceNames.INVENTORY_SERVICE,
                                    Set.of(PermissionCodes.INVENTORY_INTERNAL_RELEASE)
                            ),
                            principal,
                            MDC.get(CorrelationId.MDC_KEY)
                    )::contribute)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T execute(Supplier<T> supplier) {
        return downstreamClientFactory.execute(operation("inventory"), circuitBreaker, supplier, errorMapper);
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }
}
