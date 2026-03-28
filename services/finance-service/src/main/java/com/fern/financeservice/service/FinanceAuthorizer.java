package com.fern.financeservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class FinanceAuthorizer {
    public void requirePermission(FernPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    public void requireRegionPermission(FernPrincipal principal, Long regionId, String permission) {
        requirePermission(principal, permission);
        if (principal.scopeRoots().system() || principal.scopeRoots().regions().contains(regionId)) {
            return;
        }
        throw new ForbiddenException("Region is outside the current scope");
    }

    public void requireSystemOrPermission(FernPrincipal principal, String permission) {
        requirePermission(principal, permission);
    }
}
