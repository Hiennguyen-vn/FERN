package com.fern.iamservice.dto;

import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record EffectiveAccessResponse(
        Long userId,
        Set<String> roles,
        Set<String> grantedPermissions,
        Set<String> deniedPermissions,
        Set<String> effectivePermissions,
        ScopeRoots scopeRoots,
        Map<String, List<String>> sources
) {
}
