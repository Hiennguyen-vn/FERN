package com.fern.posservice.service;

import com.fern.platform.common.PermissionCodes;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import org.slf4j.MDC;
import com.fern.platform.observability.CorrelationId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PosOrgClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public PosOrgClient(
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
        OutletRoute response = downstreamClientFactory.execute(operation("require-outlet"), circuitBreaker, () -> restClient.get()
                .uri("/outlets/{id}", outletId)
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                PosServiceNames.POS_SERVICE,
                                "org-service",
                                Set.of(PermissionCodes.ORG_OUTLET_READ)
                        ),
                        null,
                        MDC.get(CorrelationId.MDC_KEY)
                )::contribute)
                .retrieve()
                .body(OutletRoute.class), errorMapper);
            if (response == null || response.id() == null || response.regionId() == null) {
                throw new IllegalStateException("Org service returned an invalid outlet response for outlet " + outletId);
            }
            return response;
    }

    public RegionRoute requireRegion(Long regionId) {
        RegionRoute response = downstreamClientFactory.execute(operation("require-region"), circuitBreaker, () -> restClient.get()
                .uri("/regions/{id}", regionId)
                .headers(FernDownstreamHeadersContributor.bearerToken(
                        serviceTokenSupport.issueToken(
                                PosServiceNames.POS_SERVICE,
                                "org-service",
                                Set.of(PermissionCodes.ORG_REGION_READ)
                        ),
                        null,
                        MDC.get(CorrelationId.MDC_KEY)
                )::contribute)
                .retrieve()
                .body(RegionRoute.class), errorMapper);
            if (response == null || response.id() == null || response.currencyCode() == null || response.timezoneName() == null) {
                throw new IllegalStateException("Org service returned an invalid region response for region " + regionId);
            }
            return response;
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }

    public record OutletRoute(Long id, Long regionId, String status, LocalDate closedAt) {
        public OutletRoute {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(regionId, "regionId");
        }

        public boolean isActive() {
            return status == null || "ACTIVE".equalsIgnoreCase(status);
        }

        public boolean isClosedOn(LocalDate businessDate) {
            return closedAt != null && !closedAt.isAfter(businessDate);
        }
    }

    public record RegionRoute(Long id, String currencyCode, String timezoneName) {
        public RegionRoute {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(currencyCode, "currencyCode");
            Objects.requireNonNull(timezoneName, "timezoneName");
        }
    }
}
