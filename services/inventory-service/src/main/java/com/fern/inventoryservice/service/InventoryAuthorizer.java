package com.fern.inventoryservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class InventoryAuthorizer {
    public void requirePermission(FernPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    public void requireOutletAccess(FernPrincipal principal, Long outletId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system()) {
            return;
        }
        if (principal.scopeRoots().outlets().contains(outletId)) {
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
