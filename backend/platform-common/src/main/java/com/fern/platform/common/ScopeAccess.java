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

    public static boolean allowsRoute(FernPrincipal principal, Long regionId, Long outletId) {
        ScopeRoots scope = accessibleScope(principal);
        if (scope.system()) {
            return true;
        }
        return (outletId != null && scope.outlets().contains(outletId))
                || (regionId != null && scope.regions().contains(regionId));
    }
}
