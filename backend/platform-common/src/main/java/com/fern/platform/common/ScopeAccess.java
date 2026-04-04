package com.fern.platform.common;

public final class ScopeAccess {
    private ScopeAccess() {
    }

    public static ScopeRoots accessibleScope(FernPrincipal principal) {
        if (principal == null) {
            return ScopeRoots.empty();
        }
        if (principal.accessibleScope() != null) {
            return principal.accessibleScope();
        }
        if (principal.scopeRoots() != null) {
            return principal.scopeRoots();
        }
        return ScopeRoots.empty();
    }

    public static boolean isSystemScoped(FernPrincipal principal) {
        return accessibleScope(principal).system();
    }

    public static boolean allowsRegion(FernPrincipal principal, Long regionId) {
        if (regionId == null) {
            return false;
        }
        ScopeRoots scope = accessibleScope(principal);
        return scope.system() || scope.regions().contains(regionId);
    }

    public static boolean allowsOutlet(FernPrincipal principal, Long outletId) {
        if (outletId == null) {
            return false;
        }
        ScopeRoots scope = accessibleScope(principal);
        return scope.system() || scope.outlets().contains(outletId);
    }

    /**
     * Checks if the principal has access to the given route (region + outlet).
     *
     * <p>Security fix: When {@code outletId} is present, we check outlet-level access
     * exclusively. This prevents a regional user from accessing outlets outside their
     * scope by simply providing their valid regionId alongside an unauthorized outletId.
     *
     * <p>When only {@code regionId} is provided (outletId is null), we fall back to
     * region-level access check — this is the correct behavior for region-scoped
     * operations like payroll or reporting.
     */
    public static boolean allowsRoute(FernPrincipal principal, Long regionId, Long outletId) {
        ScopeRoots scope = accessibleScope(principal);
        if (scope.system()) {
            return true;
        }
        // Outlet-level operations: require outlet scope (the most specific level).
        // A regional user who does NOT have the specific outlet in their scope must be denied.
        if (outletId != null) {
            return scope.outlets().contains(outletId);
        }
        // Region-level operations (no outlet specified): fall back to region scope.
        return regionId != null && scope.regions().contains(regionId);
    }
}
