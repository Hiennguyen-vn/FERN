package com.fern.reportservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class ReportAuthorizer {
    public void requireRegionPermission(FernPrincipal principal, Long regionId, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
        if (principal.scopeRoots().system() || principal.scopeRoots().regions().contains(regionId)) {
            return;
        }
        throw new ForbiddenException("Region is outside the current scope");
    }
}
