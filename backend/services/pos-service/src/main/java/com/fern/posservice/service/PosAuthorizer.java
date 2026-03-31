package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeAccess;
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
        if (!ScopeAccess.allowsOutlet(principal, outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }

    public void requireRoutePermission(FernPrincipal principal, Long regionId, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (!ScopeAccess.allowsRoute(principal, regionId, outletId)) {
            if (outletId != null) {
                throw new ForbiddenException("Outlet is outside the current scope");
            }
            if (regionId != null) {
                throw new ForbiddenException("Region is outside the current scope");
            }
            throw new ForbiddenException("Resource is outside the current scope");
        }
    }
}
