package com.fern.reportservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import com.fern.reportservice.dto.ReportRevenueResponses.OutletTodayStatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ReportPosClient {
    private static final ParameterizedTypeReference<List<OutletTodayStatResponse>> OUTLET_STATS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public ReportPosClient(
            @Qualifier("reportPosRestClient") RestClient restClient,
            @Qualifier("reportPosCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("reportPosClientSpec") FernDownstreamClientSpec clientSpec,
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

    public List<OutletTodayStatResponse> fetchOutletTodayStats(List<Long> outletIds, FernPrincipal actor, String correlationId) {
        List<OutletTodayStatResponse> response = downstreamClientFactory.execute(
                new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), "outlet-today-stats", clientSpec.properties()),
                circuitBreaker,
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/pos-stats/today")
                                .queryParam("outletIds", outletIds.toArray())
                                .build())
                        .headers(FernDownstreamHeadersContributor.bearerToken(
                                serviceTokenSupport.issueToken(
                                        "report-service",
                                        "pos-service",
                                        Set.of(PermissionCodes.POS_SESSION_READ)
                                ),
                                actor,
                                correlationId
                        )::contribute)
                        .retrieve()
                        .body(OUTLET_STATS_TYPE),
                errorMapper
        );
        return response == null ? List.of() : response;
    }
}
