package com.fern.reportservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeAccess;
import com.fern.reportservice.dto.ReportRevenueResponses.OutletTodayStatResponse;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportRevenueService {
    private final ReportPosClient reportPosClient;

    public ReportRevenueService(ReportPosClient reportPosClient) {
        this.reportPosClient = reportPosClient;
    }

    @Transactional(readOnly = true)
    public List<OutletTodayStatResponse> listOutletTodayStats(FernPrincipal principal, List<Long> outletIds, String correlationId) {
        List<Long> normalizedOutletIds = normalizeOutletIds(outletIds);
        if (normalizedOutletIds.isEmpty()) {
            return List.of();
        }
        requireRevenueRead(principal);
        if (!ScopeAccess.isSystemScoped(principal)) {
            for (Long outletId : normalizedOutletIds) {
                if (!ScopeAccess.allowsOutlet(principal, outletId)) {
                    throw new ForbiddenException("Outlet is outside the current scope");
                }
            }
        }
        return reportPosClient.fetchOutletTodayStats(normalizedOutletIds, principal, correlationId);
    }

    private void requireRevenueRead(FernPrincipal principal) {
        if (principal == null || !principal.permissions().contains(PermissionCodes.REPORT_READ)) {
            throw new ForbiddenException("Missing permission");
        }
    }

    private List<Long> normalizeOutletIds(List<Long> outletIds) {
        if (outletIds == null || outletIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long outletId : outletIds) {
            if (outletId == null || outletId <= 0) {
                throw new BadRequestException("outletIds must contain only positive values");
            }
            unique.add(outletId);
        }
        return List.copyOf(unique);
    }
}
