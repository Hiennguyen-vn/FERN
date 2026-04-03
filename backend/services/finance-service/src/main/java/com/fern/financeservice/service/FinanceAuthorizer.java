package com.fern.financeservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeAccess;
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
        if (ScopeAccess.allowsRegion(principal, regionId)) {
            return;
        }
        throw new ForbiddenException("Region is outside the current scope");
    }

    public void requireSystemPermission(FernPrincipal principal, String permission) {
        requirePermission(principal, permission);
        if (ScopeAccess.isSystemScoped(principal)) {
            return;
        }
        throw new ForbiddenException("System scope is required");
    }

    public void requireSystemOrPermission(FernPrincipal principal, String permission) {
        requireSystemPermission(principal, permission);
    }

    public void requireInternalPermission(FernPrincipal principal, String permission) {
        requireSystemPermission(principal, permission);
    }
}
