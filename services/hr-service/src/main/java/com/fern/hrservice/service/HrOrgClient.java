package com.fern.hrservice.service;

import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.security.FernServiceTokenSupport;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HrOrgClient {
    private static final String HR_SERVICE = "hr-service";

    private final RestClient restClient;
    private final FernServiceTokenSupport serviceTokenSupport;

    public HrOrgClient(
            @Qualifier("orgRestClient") RestClient restClient,
            FernServiceTokenSupport serviceTokenSupport
    ) {
        this.restClient = restClient;
        this.serviceTokenSupport = serviceTokenSupport;
    }

    public OutletRoute requireOutlet(Long outletId) {
        try {
            OutletRoute response = restClient.get()
                    .uri("/outlets/{id}", outletId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceTokenSupport.issueToken(HR_SERVICE, Set.of(PermissionCodes.ORG_OUTLET_READ))
                    )
                    .retrieve()
                    .body(OutletRoute.class);
            if (response == null || response.id() == null || response.regionId() == null) {
                throw new IllegalStateException("Org service returned an invalid outlet response for outlet " + outletId);
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Outlet not found: " + outletId);
            }
            throw new IllegalStateException("Unable to resolve outlet " + outletId + " from org-service", exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException("org-service is unavailable while resolving outlet " + outletId, exception);
        }
    }

    public record OutletRoute(Long id, Long regionId) {
        public OutletRoute {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(regionId, "regionId");
        }
    }
}
