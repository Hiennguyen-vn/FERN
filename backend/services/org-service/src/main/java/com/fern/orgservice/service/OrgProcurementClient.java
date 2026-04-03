package com.fern.orgservice.service;

import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrgProcurementClient {
    private static final String ORG_SERVICE = "org-service";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public OrgProcurementClient(
            @Qualifier("procurementRestClient") RestClient restClient,
            @Qualifier("procurementCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("procurementClientSpec") FernDownstreamClientSpec clientSpec,
            FernServiceTokenSupport serviceTokenSupport,
            FernDownstreamClientFactory downstreamClientFactory,
            FernDownstreamErrorMapper errorMapper
    ) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.clientSpec = clientSpec;
        this.serviceTokenSupport = serviceTokenSupport;
        this.downstreamClientFactory = downstreamClientFactory;
        this.errorMapper = errorMapper;
    }

    public OutletCloseCheck getOutletCloseCheck(Long outletId, FernPrincipal actor) {
        OutletCloseCheck response = downstreamClientFactory.execute(operation("outlet-close-check"), circuitBreaker, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/procurement/outlet-close-check")
                        .queryParam("outletId", outletId)
                        .build())
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                ORG_SERVICE,
                                "procurement-service",
                                Set.of(PermissionCodes.PROCUREMENT_INTERNAL_READ)
                        ),
                        actor,
                        MDC.get(com.fern.platform.observability.CorrelationId.MDC_KEY)
                )::contribute)
                .retrieve()
                .body(OutletCloseCheck.class), errorMapper);
            if (response == null) {
                throw new DownstreamUnavailableException(
                        "procurement-service returned an empty outlet close check response for outlet " + outletId
                );
            }
            return response;
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }

    public record OutletCloseCheck(
            Long outletId,
            long blockingPurchaseOrders,
            long blockingGoodsReceipts,
            long blockingSupplierInvoices,
            boolean hasBlockingDocuments
    ) {
    }
}
