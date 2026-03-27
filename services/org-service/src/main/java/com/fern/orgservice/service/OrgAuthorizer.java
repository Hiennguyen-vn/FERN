package com.fern.orgservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class OrgAuthorizer {
    private final ScopeExpansionService scopeExpansionService;

    public OrgAuthorizer(ScopeExpansionService scopeExpansionService) {
        this.scopeExpansionService = scopeExpansionService;
    }

    public void requirePermission(FernPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    public void requireRegionAccess(FernPrincipal principal, Long regionId, String permission) {
        requirePermission(principal, permission);
        var expanded = scopeExpansionService.expand(principal.scopeRoots().regions(), principal.scopeRoots().outlets());
        if (!expanded.regionIds().contains(regionId)) {
            throw new ForbiddenException("Region is outside the current scope");
        }
    }

    public void requireOutletAccess(FernPrincipal principal, Long outletId, String permission) {
        requirePermission(principal, permission);
        var expanded = scopeExpansionService.expand(principal.scopeRoots().regions(), principal.scopeRoots().outlets());
        if (!expanded.outletIds().contains(outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }
}
