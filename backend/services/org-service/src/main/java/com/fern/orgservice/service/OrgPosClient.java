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
import java.util.List;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrgPosClient {
    private static final String ORG_SERVICE = "org-service";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public OrgPosClient(
            @Qualifier("posRestClient") RestClient restClient,
            @Qualifier("posCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("posClientSpec") FernDownstreamClientSpec clientSpec,
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

    public boolean hasOpenSessions(Long outletId, FernPrincipal actor) {
        List<PosSessionSummary> response = downstreamClientFactory.execute(operation("has-open-sessions"), circuitBreaker, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/pos-sessions")
                        .queryParam("outletId", outletId)
                        .queryParam("status", "OPEN")
                        .queryParam("limit", 1)
                        .build())
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                ORG_SERVICE,
                                "pos-service",
                                Set.of(PermissionCodes.POS_SESSION_READ)
                        ),
                        actor,
                        MDC.get(com.fern.platform.observability.CorrelationId.MDC_KEY)
                )::contribute)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}), errorMapper);
            return response != null && !response.isEmpty();
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }

    private record PosSessionSummary(Long id, String status) {
    }
}
