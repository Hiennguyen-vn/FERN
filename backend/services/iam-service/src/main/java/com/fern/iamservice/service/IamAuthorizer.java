package com.fern.iamservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import org.springframework.stereotype.Component;

@Component
public class IamAuthorizer {
    public void requirePermission(FernPrincipal principal, String permission) {
        if (principal == null || !principal.permissions().contains(permission)) {
            throw new ForbiddenException("Missing permission: " + permission);
        }
    }

    public void requirePermissionOrSelf(FernPrincipal principal, String permission, Long userId) {
        if (principal != null && principal.userId() != null && principal.userId().equals(userId)) {
            return;
        }

        requirePermission(principal, permission);
    }
}
