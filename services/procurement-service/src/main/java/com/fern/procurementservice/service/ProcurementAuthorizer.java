package com.fern.procurementservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class ProcurementAuthorizer {
    public void requirePermission(FernPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    public void requireOutletPermission(FernPrincipal principal, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system()) {
            return;
        }
        if (!principal.scopeRoots().outlets().isEmpty() && !principal.scopeRoots().outlets().contains(outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }

    public void requireRegionPermission(FernPrincipal principal, Long regionId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system()) {
            return;
        }
        if (!principal.scopeRoots().regions().isEmpty() && !principal.scopeRoots().regions().contains(regionId)) {
            throw new ForbiddenException("Region is outside the current scope");
        }
    }

    public void requireRouteRead(FernPrincipal principal, Long regionId, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system()) {
            return;
        }
        boolean outletAllowed = principal.scopeRoots().outlets().isEmpty() || principal.scopeRoots().outlets().contains(outletId);
        boolean regionAllowed = principal.scopeRoots().regions().isEmpty() || principal.scopeRoots().regions().contains(regionId);
        if (!outletAllowed && !regionAllowed) {
            throw new ForbiddenException("Resource is outside the current scope");
        }
    }
}
