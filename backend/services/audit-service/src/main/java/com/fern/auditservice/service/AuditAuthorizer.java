package com.fern.auditservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import org.springframework.stereotype.Component;

@Component
public class AuditAuthorizer {
    public void requireRead(FernPrincipal principal) {
        if (principal == null || !principal.permissions().contains(PermissionCodes.AUDIT_READ)) {
            throw new ForbiddenException("Missing permission: " + PermissionCodes.AUDIT_READ);
        }
    }

    public boolean canReadDetails(FernPrincipal principal) {
        return principal != null && principal.permissions().contains(PermissionCodes.AUDIT_DETAIL_READ);
    }
}
