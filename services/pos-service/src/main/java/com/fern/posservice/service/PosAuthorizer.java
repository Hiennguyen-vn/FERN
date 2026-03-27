package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class PosAuthorizer {
    public void requireOutletPermission(FernPrincipal principal, Long outletId, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
        if (principal.scopeRoots().system()) {
            return;
        }
        if (!principal.scopeRoots().outlets().contains(outletId)) {
            throw new ForbiddenException("Outlet is outside the current scope");
        }
    }
}
