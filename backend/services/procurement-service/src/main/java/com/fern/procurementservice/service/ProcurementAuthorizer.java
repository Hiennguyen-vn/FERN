package com.fern.procurementservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeAccess;
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
        if (!ScopeAccess.allowsOutlet(principal, outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }

    public void requireRegionPermission(FernPrincipal principal, Long regionId, String permission) {
        requirePermission(principal, permission);
        if (!ScopeAccess.allowsRegion(principal, regionId)) {
            throw new ForbiddenException("Region is outside the current scope");
        }
    }

    public void requireRouteRead(FernPrincipal principal, Long regionId, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (!ScopeAccess.allowsRoute(principal, regionId, outletId)) {
            throw new ForbiddenException("Resource is outside the current scope");
        }
    }
}
