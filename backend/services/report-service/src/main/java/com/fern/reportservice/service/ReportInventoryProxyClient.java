package com.fern.reportservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class ReportInventoryProxyClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public ReportInventoryProxyClient(
            @Qualifier("reportInventoryRestClient") RestClient restClient,
            @Qualifier("reportInventoryCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("reportInventoryClientSpec") FernDownstreamClientSpec clientSpec,
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

    public Object fetchStockBalances(MultiValueMap<String, String> params, FernPrincipal actor, String correlationId) {
        return fetch("/stock-balances", "stock-balances", params, actor, correlationId);
    }

    public Object fetchInventoryTransactions(MultiValueMap<String, String> params, FernPrincipal actor, String correlationId) {
        return fetch("/inventory-transactions", "inventory-transactions", params, actor, correlationId);
    }

    private Object fetch(
            String path,
            String operation,
            MultiValueMap<String, String> params,
            FernPrincipal actor,
            String correlationId
    ) {
        return downstreamClientFactory.execute(
                new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties()),
                circuitBreaker,
                () -> restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(path);
                            if (params != null) {
                                params.forEach((key, values) -> {
                                    if (values != null) {
                                        values.forEach(value -> uriBuilder.queryParam(key, value));
                                    }
                                });
                            }
                            return uriBuilder.build();
                        })
                        .headers(FernDownstreamHeadersContributor.bearerToken(
                                serviceTokenSupport.issueToken(
                                        "report-service",
                                        "inventory-service",
                                        Set.of(PermissionCodes.INVENTORY_LEDGER_READ, PermissionCodes.INVENTORY_BALANCE_READ)
                                ),
                                actor,
                                correlationId
                        )::contribute)
                        .retrieve()
                        .body(Object.class),
                errorMapper
        );
    }
}
