package com.fern.financeservice.service;

import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PayrollHrClient {
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final String hrBaseUrl;

    public PayrollHrClient(
            RestClient restClient,
            @Qualifier("hrCircuitBreaker") CircuitBreaker circuitBreaker,
            FernServiceTokenSupport serviceTokenSupport,
            @Value("${fern.clients.hr-base-url}") String hrBaseUrl
    ) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.serviceTokenSupport = serviceTokenSupport;
        this.hrBaseUrl = hrBaseUrl;
    }

    public List<EffectiveContract> fetchEffectiveContracts(Long regionId, LocalDate startDate, LocalDate endDate, String correlationId, Long actorUserId) {
        return execute(() -> {
            EffectiveContract[] response = restClient.get()
                    .uri(hrBaseUrl + "/internal/hr/effective-contracts?regionId=" + regionId + "&startDate=" + startDate + "&endDate=" + endDate)
                    .headers(headers -> applyInternalHeaders(headers, Set.of(PermissionCodes.HR_INTERNAL_READ), correlationId, actorUserId))
                    .retrieve()
                    .body(EffectiveContract[].class);
            return response == null ? List.of() : List.of(response);
        }, "effective contracts");
    }

    public List<ApprovedAttendance> fetchApprovedAttendance(Long regionId, LocalDate startDate, LocalDate endDate, String correlationId, Long actorUserId) {
        return execute(() -> {
            ApprovedAttendance[] response = restClient.get()
                    .uri(hrBaseUrl + "/internal/hr/approved-attendance?regionId=" + regionId + "&startDate=" + startDate + "&endDate=" + endDate)
                    .headers(headers -> applyInternalHeaders(headers, Set.of(PermissionCodes.HR_INTERNAL_READ), correlationId, actorUserId))
                    .retrieve()
                    .body(ApprovedAttendance[].class);
            return response == null ? List.of() : List.of(response);
        }, "approved attendance");
    }

    private void applyInternalHeaders(HttpHeaders headers, Collection<String> permissions, String correlationId, Long actorUserId) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenSupport.issueToken("finance-service", "hr-service", permissions));
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
        if (actorUserId != null) {
            headers.set(FernRequestHeaders.ACTOR_USER_ID, actorUserId.toString());
        }
    }

    private <T> T execute(Supplier<T> supplier, String operation) {
        try {
            return circuitBreaker.executeSupplier(supplier);
        } catch (CallNotPermittedException exception) {
            throw new BadRequestException("Unable to fetch " + operation + " from HR: circuit breaker is open");
        } catch (RestClientResponseException exception) {
            throw new BadRequestException("Unable to fetch " + operation + " from HR: " + exception.getMessage());
        } catch (RestClientException exception) {
            throw new BadRequestException("Unable to fetch " + operation + " from HR: " + exception.getMessage());
        }
    }
}
