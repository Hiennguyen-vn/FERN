package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record FernJwtClaims(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        ScopeRoots scopeRoots,
        long policyVersion,
        long scopeVersion,
        String jti,
        Instant authTime,
        Instant expiresAt,
        FernPrincipalType principalType
) {
    public FernJwtClaims(
            Long userId,
            String username,
            Set<String> roles,
            Set<String> permissions,
            ScopeRoots scopeRoots,
            long policyVersion,
            long scopeVersion,
            String jti,
            Instant authTime,
            Instant expiresAt
    ) {
        this(userId, username, roles, permissions, scopeRoots, policyVersion, scopeVersion, jti, authTime, expiresAt, FernPrincipalType.USER);
    }

    public FernPrincipal toPrincipal() {
        return new FernPrincipal(userId, username, roles, permissions, scopeRoots, policyVersion, scopeVersion, jti, principalType);
    }

    @SuppressWarnings("unchecked")
    public static FernJwtClaims fromMap(org.springframework.security.oauth2.jwt.Jwt jwt) {
        Number userId = jwt.getClaim("user_id");
        List<String> roles = jwt.getClaim("roles");
        List<String> permissions = jwt.getClaim("permissions");
        var scopeRootsMap = (java.util.Map<String, Object>) jwt.getClaim("scope_roots");
        boolean system = scopeRootsMap != null && Boolean.TRUE.equals(scopeRootsMap.get("system"));
        List<Long> regions = scopeRootsMap == null ? List.of() : ((List<Number>) scopeRootsMap.getOrDefault("regions", List.of())).stream().map(Number::longValue).toList();
        List<Long> outlets = scopeRootsMap == null ? List.of() : ((List<Number>) scopeRootsMap.getOrDefault("outlets", List.of())).stream().map(Number::longValue).toList();
        Number authTime = jwt.getClaim("auth_time");
        Number policyVersion = jwt.getClaim("policy_version");
        Number scopeVersion = jwt.getClaim("scope_version");
        String principalType = jwt.getClaimAsString("principal_type");

        return new FernJwtClaims(
                userId == null ? null : userId.longValue(),
                jwt.getSubject(),
                Set.copyOf(roles == null ? List.of() : roles),
                Set.copyOf(permissions == null ? List.of() : permissions),
                new ScopeRoots(system, regions, outlets),
                policyVersion == null ? 0L : policyVersion.longValue(),
                scopeVersion == null ? 0L : scopeVersion.longValue(),
                jwt.getId(),
                authTime == null ? Instant.now() : Instant.ofEpochSecond(authTime.longValue()),
                jwt.getExpiresAt(),
                principalType == null ? FernPrincipalType.USER : FernPrincipalType.valueOf(principalType)
        );
    }
}
