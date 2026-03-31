package com.fern.reportservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeAccess;
import org.springframework.stereotype.Component;

@Component
public class ReportAuthorizer {
    public void requireRegionReportRead(FernPrincipal principal, Long regionId) {
        requireRegionPermission(principal, regionId, PermissionCodes.REPORT_READ, PermissionCodes.REPORT_PAYROLL_READ);
    }

    public void requireRegionReportExport(FernPrincipal principal, Long regionId) {
        requireRegionPermission(principal, regionId, PermissionCodes.REPORT_EXPORT, PermissionCodes.REPORT_PAYROLL_EXPORT);
    }

    public void requireRegionPayrollRead(FernPrincipal principal, Long regionId) {
        requireRegionPermission(principal, regionId, PermissionCodes.REPORT_PAYROLL_READ, PermissionCodes.REPORT_READ);
    }

    public void requireRegionPayrollExport(FernPrincipal principal, Long regionId) {
        requireRegionPermission(principal, regionId, PermissionCodes.REPORT_PAYROLL_EXPORT, PermissionCodes.REPORT_EXPORT);
    }

    public void requireCompanyReportRead(FernPrincipal principal) {
        requireSystemPermission(principal, PermissionCodes.REPORT_READ, PermissionCodes.REPORT_PAYROLL_READ);
    }

    public void requireCompanyReportExport(FernPrincipal principal) {
        requireSystemPermission(principal, PermissionCodes.REPORT_EXPORT, PermissionCodes.REPORT_PAYROLL_EXPORT);
    }

    private void requireRegionPermission(FernPrincipal principal, Long regionId, String primaryPermission, String secondaryPermission) {
        if (principal == null || !hasAnyPermission(principal, primaryPermission, secondaryPermission)) {
            throw new ForbiddenException("Missing permission");
        }
        if (ScopeAccess.isSystemScoped(principal)) {
            return;
        }
        if (!ScopeAccess.allowsRegion(principal, regionId)) {
            throw new ForbiddenException("Region is outside the current scope");
        }
    }

    private void requireSystemPermission(FernPrincipal principal, String primaryPermission, String secondaryPermission) {
        if (principal == null || !hasAnyPermission(principal, primaryPermission, secondaryPermission)) {
            throw new ForbiddenException("Missing permission");
        }
        if (!ScopeAccess.isSystemScoped(principal)) {
            throw new ForbiddenException("System scope is required");
        }
    }

    private boolean hasAnyPermission(FernPrincipal principal, String... permissions) {
        for (String permission : permissions) {
            if (principal.permissions().contains(permission)) {
                return true;
            }
        }
        return false;
    }
}
