package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PosInventoryClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final PosInternalClientSupport internalClientSupport;
    private final PosDownstreamErrorHandler errorHandler;

    public PosInventoryClient(
            @Qualifier("inventoryRestClient") RestClient restClient,
            CircuitBreaker inventoryCircuitBreaker,
            PosInternalClientSupport internalClientSupport,
            PosDownstreamErrorHandler errorHandler
    ) {
        this.restClient = restClient;
        this.circuitBreaker = inventoryCircuitBreaker;
        this.internalClientSupport = internalClientSupport;
        this.errorHandler = errorHandler;
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
                .headers(headers -> internalClientSupport.applyInternalHeaders(
                        headers,
                        principal,
                        Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)
                ))
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

    private <T> T execute(Supplier<T> supplier) {
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException exception) {
            throw errorHandler.serviceUnavailable(PosServiceNames.INVENTORY_SERVICE, exception);
        } catch (RestClientResponseException exception) {
            throw errorHandler.translateResponse(PosServiceNames.INVENTORY_SERVICE, exception);
        } catch (RestClientException exception) {
            throw errorHandler.serviceUnavailable(PosServiceNames.INVENTORY_SERVICE, exception);
        }
    }
}
