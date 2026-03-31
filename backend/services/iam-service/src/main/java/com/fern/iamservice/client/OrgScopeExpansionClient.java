package com.fern.iamservice.client;

import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernServiceTokenSupport;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OrgScopeExpansionClient {
    private static final String IAM_SERVICE = "iam-service";

    private final RestClient restClient;
    private final FernServiceTokenSupport serviceTokenSupport;

    public OrgScopeExpansionClient(
            @Qualifier("orgRestClient") RestClient restClient,
            FernServiceTokenSupport serviceTokenSupport
    ) {
        this.restClient = restClient;
        this.serviceTokenSupport = serviceTokenSupport;
    }

    public ScopeRoots expand(ScopeRoots scopeRoots) {
        ScopeRoots requestedScope = scopeRoots == null ? ScopeRoots.empty() : scopeRoots;
        if (requestedScope.system()) {
            return requestedScope;
        }
        if (requestedScope.regions().isEmpty() && requestedScope.outlets().isEmpty()) {
            return requestedScope;
        }
        try {
            ExpandedScopeResponse response = restClient.post()
                    .uri("/internal/scopes/expand")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenSupport.issueToken(
                                    IAM_SERVICE,
                                    "org-service",
                                    Set.of(PermissionCodes.ORG_SCOPE_RESOLVE)
                            )
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ScopeExpansionRequest(requestedScope.regions(), requestedScope.outlets()))
                    .retrieve()
                    .body(ExpandedScopeResponse.class);
            if (response == null) {
                throw new DownstreamUnavailableException("org-service returned an empty scope expansion response");
            }
            return new ScopeRoots(requestedScope.system(), response.regionIds(), response.outletIds());
        } catch (RestClientException exception) {
            throw new DownstreamUnavailableException("org-service is unavailable while expanding user scope", exception);
        }
    }

    private record ScopeExpansionRequest(List<Long> regionIds, List<Long> outletIds) {
    }

    private record ExpandedScopeResponse(List<Long> regionIds, List<Long> outletIds, long scopeVersion) {
    }
}
