package com.fern.platform.common;

public final class ScopeService {
    private ScopeService() {
    }

    public static void validateScope(ScopeRoots principalScopeRoots, ScopeRoots requestedScopeRoots) {
        ScopeRoots effectivePrincipal = principalScopeRoots == null ? ScopeRoots.empty() : principalScopeRoots;
        ScopeRoots effectiveRequested = requestedScopeRoots == null ? ScopeRoots.empty() : requestedScopeRoots;
        if (effectivePrincipal.system()) {
            return;
        }
        if (effectiveRequested.system()) {
            throw new ForbiddenException("Requested scope is outside the current principal scope");
        }
        if (!effectivePrincipal.regions().containsAll(effectiveRequested.regions())
                || !effectivePrincipal.outlets().containsAll(effectiveRequested.outlets())) {
            throw new ForbiddenException("Requested scope is outside the current principal scope");
        }
    }
}
