package com.fern.catalogservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class CatalogAuthorizer {
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

    public void requireInternalPermission(FernPrincipal principal, String permission) {
        requirePermission(principal, permission);
        if (!principal.isService()) {
            throw new ForbiddenException("Service principal is required");
        }
    }
}
