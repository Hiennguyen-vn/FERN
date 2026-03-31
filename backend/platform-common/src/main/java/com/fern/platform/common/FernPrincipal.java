package com.fern.platform.common;

import java.util.Set;

public record FernPrincipal(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        ScopeRoots scopeRoots,
        ScopeRoots accessibleScope,
        long policyVersion,
        long scopeVersion,
        String jti,
        FernPrincipalType principalType
) {
    public FernPrincipal(
            Long userId,
            String username,
            Set<String> roles,
            Set<String> permissions,
            ScopeRoots scopeRoots,
            long policyVersion,
            long scopeVersion,
            String jti
    ) {
        this(userId, username, roles, permissions, scopeRoots, scopeRoots, policyVersion, scopeVersion, jti, FernPrincipalType.USER);
    }

    public FernPrincipal(
            Long userId,
            String username,
            Set<String> roles,
            Set<String> permissions,
            ScopeRoots scopeRoots,
            long policyVersion,
            long scopeVersion,
            String jti,
            FernPrincipalType principalType
    ) {
        this(userId, username, roles, permissions, scopeRoots, scopeRoots, policyVersion, scopeVersion, jti, principalType);
    }

    public FernPrincipal {
        scopeRoots = scopeRoots == null ? ScopeRoots.empty() : scopeRoots;
        accessibleScope = accessibleScope == null ? scopeRoots : accessibleScope;
    }

    public boolean isService() {
        return principalType == FernPrincipalType.SERVICE;
    }

    public boolean isSystemScoped() {
        return accessibleScope.system();
    }
}
