package com.fern.platform.common;

import java.util.Set;

public record FernPrincipal(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        ScopeRoots scopeRoots,
        long policyVersion,
        long scopeVersion,
        String jti
) {
}
