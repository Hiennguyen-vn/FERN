package com.fern.iamservice.client;

import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrgScopeExpansionClient {
    private static final String IAM_SERVICE = "iam-service";

    private final RestClient restClient;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public OrgScopeExpansionClient(
            @Qualifier("orgRestClient") RestClient restClient,
            @Qualifier("orgClientSpec") FernDownstreamClientSpec clientSpec,
            FernServiceTokenSupport serviceTokenSupport,
            FernDownstreamClientFactory downstreamClientFactory,
            FernDownstreamErrorMapper errorMapper
    ) {
        this.restClient = restClient;
        this.clientSpec = clientSpec;
        this.serviceTokenSupport = serviceTokenSupport;
        this.downstreamClientFactory = downstreamClientFactory;
        this.errorMapper = errorMapper;
    }

    public ScopeRoots expand(ScopeRoots scopeRoots) {
        ScopeRoots requestedScope = scopeRoots == null ? ScopeRoots.empty() : scopeRoots;
        if (requestedScope.system()) {
            return requestedScope;
        }
        if (requestedScope.regions().isEmpty() && requestedScope.outlets().isEmpty()) {
            return requestedScope;
        }
        ExpandedScopeResponse response = downstreamClientFactory.execute(operation("scope-expand"), null, () -> restClient.post()
                .uri("/internal/scopes/expand")
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                IAM_SERVICE,
                                "org-service",
                                Set.of(PermissionCodes.ORG_SCOPE_RESOLVE)
                        ),
                        null,
                        null
                )::contribute)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ScopeExpansionRequest(requestedScope.regions(), requestedScope.outlets()))
                .retrieve()
                .body(ExpandedScopeResponse.class), errorMapper);
            if (response == null) {
                throw new DownstreamUnavailableException("org-service returned an empty scope expansion response");
            }
            return new ScopeRoots(requestedScope.system(), response.regionIds(), response.outletIds());
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }

    private record ScopeExpansionRequest(List<Long> regionIds, List<Long> outletIds) {
    }

    private record ExpandedScopeResponse(List<Long> regionIds, List<Long> outletIds, long scopeVersion) {
    }
}
