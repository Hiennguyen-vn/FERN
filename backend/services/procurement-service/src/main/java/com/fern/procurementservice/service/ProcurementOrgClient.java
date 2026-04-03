package com.fern.procurementservice.service;

import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Objects;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProcurementOrgClient {
    private static final String PROCUREMENT_SERVICE = "procurement-service";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public ProcurementOrgClient(
            @Qualifier("orgRestClient") RestClient restClient,
            @Qualifier("orgCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("orgClientSpec") FernDownstreamClientSpec clientSpec,
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

    public OutletRoute requireOutlet(Long outletId) {
        return downstreamClientFactory.execute(operation("require-outlet"), circuitBreaker, () -> fetchOutletWithRetry(outletId), errorMapper);
    }

    private OutletRoute fetchOutletWithRetry(Long outletId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                OutletRoute response = restClient.get()
                        .uri("/outlets/{id}", outletId)
                        .headers(FernDownstreamHeadersContributor.bearerToken(
                                serviceTokenSupport.issueToken(
                                        PROCUREMENT_SERVICE,
                                        "org-service",
                                        Set.of(PermissionCodes.ORG_OUTLET_READ)
                                ),
                                null,
                                MDC.get(CorrelationId.MDC_KEY)
                        )::contribute)
                        .retrieve()
                        .body(OutletRoute.class);
                if (response == null || response.id() == null || response.regionId() == null) {
                    throw new DownstreamUnavailableException("Org service returned an invalid outlet response for outlet " + outletId);
                }
                return response;
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 404) {
                    throw new ResourceNotFoundException("Outlet not found: " + outletId);
                }
                if (attempt < 2 && isRetryable(exception.getStatusCode())) {
                    continue;
                }
                throw new DownstreamUnavailableException("org-service is unavailable while resolving outlet " + outletId, exception);
            } catch (RestClientException exception) {
                if (attempt < 2) {
                    continue;
                }
                throw new DownstreamUnavailableException("org-service is unavailable while resolving outlet " + outletId, exception);
            }
        }
        throw new DownstreamUnavailableException("org-service is unavailable while resolving outlet " + outletId);
    }

    private boolean isRetryable(HttpStatusCode statusCode) {
        return statusCode.is5xxServerError() || statusCode.value() == 408 || statusCode.value() == 429;
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }

    public record OutletRoute(Long id, Long regionId) {
        public OutletRoute {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(regionId, "regionId");
        }
    }
}
