package com.fern.reportservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class ReportInventoryCompatibilityService {
    private final ReportInventoryProxyClient proxyClient;
    private final ReportAuthorizer reportAuthorizer;

    public ReportInventoryCompatibilityService(
            ReportInventoryProxyClient proxyClient,
            ReportAuthorizer reportAuthorizer
    ) {
        this.proxyClient = proxyClient;
        this.reportAuthorizer = reportAuthorizer;
    }

    public Object fetchStockBalances(FernPrincipal principal, MultiValueMap<String, String> params, String correlationId) {
        reportAuthorizer.requireOutletReportRead(principal, requireOutletId(params));
        return proxyClient.fetchStockBalances(params, principal, correlationId);
    }

    public Object fetchInventoryTransactions(FernPrincipal principal, MultiValueMap<String, String> params, String correlationId) {
        reportAuthorizer.requireOutletReportRead(principal, requireOutletId(params));
        return proxyClient.fetchInventoryTransactions(params, principal, correlationId);
    }

    private Long requireOutletId(MultiValueMap<String, String> params) {
        if (params == null) {
            throw new BadRequestException("outletId is required");
        }
        String rawOutletId = params.getFirst("outletId");
        if (rawOutletId == null || rawOutletId.isBlank()) {
            throw new BadRequestException("outletId is required");
        }
        try {
            long outletId = Long.parseLong(rawOutletId);
            if (outletId <= 0) {
                throw new BadRequestException("outletId must be positive");
            }
            return outletId;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("outletId must be numeric");
        }
    }
}
