package com.fern.hrservice.service;

import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.security.FernServiceTokenSupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HrOrgClient {
    private static final String HR_SERVICE = "hr-service";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;

    public HrOrgClient(
            @Qualifier("orgRestClient") RestClient restClient,
            @Qualifier("orgCircuitBreaker") CircuitBreaker circuitBreaker,
            FernServiceTokenSupport serviceTokenSupport
    ) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.serviceTokenSupport = serviceTokenSupport;
    }

    public OutletRoute requireOutlet(Long outletId) {
        try {
            return circuitBreaker.executeSupplier(() -> fetchOutletWithRetry(outletId));
        } catch (CallNotPermittedException exception) {
            throw new DownstreamUnavailableException("org-service is unavailable while resolving outlet " + outletId + ": circuit breaker is open", exception);
        }
    }

    private OutletRoute fetchOutletWithRetry(Long outletId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                OutletRoute response = restClient.get()
                        .uri("/outlets/{id}", outletId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + serviceTokenSupport.issueToken(
                                        HR_SERVICE,
                                        "org-service",
                                        Set.of(PermissionCodes.ORG_OUTLET_READ)
                                )
                        )
                        .retrieve()
                        .body(OutletRoute.class);
                if (response == null || response.id() == null || response.regionId() == null) {
                    throw new DownstreamUnavailableException("Org service returned an invalid outlet response for outlet " + outletId);
                }
                return response;
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
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

    public record OutletRoute(Long id, Long regionId) {
        public OutletRoute {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(regionId, "regionId");
        }
    }
}
