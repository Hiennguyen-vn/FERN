package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class PosAuthorizer {
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
        if (!principal.scopeRoots().outlets().contains(outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }

    public void requireRoutePermission(FernPrincipal principal, Long regionId, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system()) {
            return;
        }
        if (regionId != null && !principal.scopeRoots().regions().contains(regionId)) {
            throw new ForbiddenException("Region is outside the current scope");
        }
        if (outletId != null && !principal.scopeRoots().outlets().contains(outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }
}
