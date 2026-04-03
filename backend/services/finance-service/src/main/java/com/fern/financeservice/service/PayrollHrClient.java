package com.fern.financeservice.service;

import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.fern.platform.web.FernDownstreamHeadersContributor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PayrollHrClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final FernDownstreamClientFactory downstreamClientFactory;
    private final FernDownstreamErrorMapper errorMapper;
    private final FernDownstreamClientSpec clientSpec;

    public PayrollHrClient(
            @Qualifier("hrRestClient") RestClient restClient,
            @Qualifier("hrCircuitBreaker") CircuitBreaker circuitBreaker,
            FernServiceTokenSupport serviceTokenSupport,
            @Qualifier("hrClientSpec") FernDownstreamClientSpec clientSpec,
            FernDownstreamClientFactory downstreamClientFactory,
            FernDownstreamErrorMapper errorMapper
    ) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.serviceTokenSupport = serviceTokenSupport;
        this.clientSpec = clientSpec;
        this.downstreamClientFactory = downstreamClientFactory;
        this.errorMapper = errorMapper;
    }

    public List<EffectiveContract> fetchEffectiveContracts(Long regionId, LocalDate startDate, LocalDate endDate, String correlationId, Long actorUserId) {
        return execute("effective-contracts", () -> {
            EffectiveContract[] response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/hr/effective-contracts")
                            .queryParam("regionId", regionId)
                            .queryParam("startDate", startDate)
                            .queryParam("endDate", endDate)
                            .build())
                    .headers(headers -> applyInternalHeaders(headers, Set.of(PermissionCodes.HR_INTERNAL_READ), correlationId, actorUserId))
                    .retrieve()
                    .body(EffectiveContract[].class);
            return response == null ? List.of() : List.of(response);
        });
    }

    public List<ApprovedAttendance> fetchApprovedAttendance(Long regionId, LocalDate startDate, LocalDate endDate, String correlationId, Long actorUserId) {
        return execute("approved-attendance", () -> {
            ApprovedAttendance[] response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/hr/approved-attendance")
                            .queryParam("regionId", regionId)
                            .queryParam("startDate", startDate)
                            .queryParam("endDate", endDate)
                            .build())
                    .headers(headers -> applyInternalHeaders(headers, Set.of(PermissionCodes.HR_INTERNAL_READ), correlationId, actorUserId))
                    .retrieve()
                    .body(ApprovedAttendance[].class);
            return response == null ? List.of() : List.of(response);
        });
    }

    private void applyInternalHeaders(org.springframework.http.HttpHeaders headers, Collection<String> permissions, String correlationId, Long actorUserId) {
        FernPrincipal actor = actorUserId == null
                ? null
                : new FernPrincipal(actorUserId, null, Set.of(), Set.of(), com.fern.platform.common.ScopeRoots.empty(), com.fern.platform.common.ScopeRoots.empty(), 0, 0, null, com.fern.platform.common.FernPrincipalType.USER);
        FernDownstreamHeadersContributor.bearerToken(
                serviceTokenSupport.issueToken(
                        "finance-service",
                        "hr-service",
                        Set.copyOf(permissions)
                ),
                actor,
                correlationId != null ? correlationId : MDC.get(CorrelationId.MDC_KEY)
        ).contribute(headers);
    }

    private <T> T execute(String operation, Supplier<T> supplier) {
        return downstreamClientFactory.execute(operation(operation), circuitBreaker, supplier, errorMapper);
    }

    private FernDownstreamClientSpec operation(String operation) {
        return new FernDownstreamClientSpec(clientSpec.callerService(), clientSpec.targetService(), operation, clientSpec.properties());
    }
}
