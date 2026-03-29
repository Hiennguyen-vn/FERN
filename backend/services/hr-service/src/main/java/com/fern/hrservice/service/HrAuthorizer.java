package com.fern.hrservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class HrAuthorizer {
    public void requirePermission(FernPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    public void requireSystemPermission(FernPrincipal principal, String permission) {
        requirePermission(principal, permission);
        if (!principal.scopeRoots().system()) {
            throw new ForbiddenException("System scope is required");
        }
    }

    public void requireRegionPermission(FernPrincipal principal, Long regionId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system() || principal.scopeRoots().regions().contains(regionId)) {
            return;
        }
        throw new ForbiddenException("Region is outside the current scope");
    }

    public void requireOutletPermission(FernPrincipal principal, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system() || principal.scopeRoots().outlets().contains(outletId)) {
            return;
        }
        throw new ForbiddenException("Outlet is outside the current scope");
    }

    public void requireInternalPermission(FernPrincipal principal, String permission) {
        requirePermission(principal, permission);
        if (!principal.isService()) {
            throw new ForbiddenException("Service principal is required");
        }
    }
}
