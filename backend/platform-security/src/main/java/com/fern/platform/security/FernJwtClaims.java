package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public record FernJwtClaims(
        Long userId,
        String username,
        Set<String> roles,
        Set<String> permissions,
        ScopeRoots scopeRoots,
        ScopeRoots accessibleScope,
        long policyVersion,
        long scopeVersion,
        String jti,
        Instant authTime,
        Instant expiresAt,
        FernPrincipalType principalType,
        String issuer,
        Set<String> audience
) {
    public FernJwtClaims {
        scopeRoots = scopeRoots == null ? ScopeRoots.empty() : scopeRoots;
        accessibleScope = accessibleScope == null ? scopeRoots : accessibleScope;
        audience = audience == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(audience));
    }

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
        this(userId, username, roles, permissions, scopeRoots, scopeRoots, policyVersion, scopeVersion, jti, authTime, expiresAt, FernPrincipalType.USER, null, Set.of());
    }

    public FernJwtClaims(
            Long userId,
            String username,
            Set<String> roles,
            Set<String> permissions,
            ScopeRoots scopeRoots,
            ScopeRoots accessibleScope,
            long policyVersion,
            long scopeVersion,
            String jti,
            Instant authTime,
            Instant expiresAt
    ) {
        this(userId, username, roles, permissions, scopeRoots, accessibleScope, policyVersion, scopeVersion, jti, authTime, expiresAt, FernPrincipalType.USER, null, Set.of());
    }

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
            Instant expiresAt,
            FernPrincipalType principalType
    ) {
        this(userId, username, roles, permissions, scopeRoots, scopeRoots, policyVersion, scopeVersion, jti, authTime, expiresAt, principalType, null, Set.of());
    }

    public FernJwtClaims(
            Long userId,
            String username,
            Set<String> roles,
            Set<String> permissions,
            ScopeRoots scopeRoots,
            ScopeRoots accessibleScope,
            long policyVersion,
            long scopeVersion,
            String jti,
            Instant authTime,
            Instant expiresAt,
            FernPrincipalType principalType
    ) {
        this(userId, username, roles, permissions, scopeRoots, accessibleScope, policyVersion, scopeVersion, jti, authTime, expiresAt, principalType, null, Set.of());
    }

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
            Instant expiresAt,
            FernPrincipalType principalType,
            String issuer,
            Set<String> audience
    ) {
        this(userId, username, roles, permissions, scopeRoots, scopeRoots, policyVersion, scopeVersion, jti, authTime, expiresAt, principalType, issuer, audience);
    }

    public FernPrincipal toPrincipal() {
        return new FernPrincipal(userId, username, roles, permissions, scopeRoots, accessibleScope, policyVersion, scopeVersion, jti, principalType);
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
        ScopeRoots scopeRoots = new ScopeRoots(system, regions, outlets);
        var accessibleScopeMap = (java.util.Map<String, Object>) jwt.getClaim("accessible_scope");
        boolean accessibleSystem = accessibleScopeMap != null && Boolean.TRUE.equals(accessibleScopeMap.get("system"));
        List<Long> accessibleRegions = accessibleScopeMap == null
                ? scopeRoots.regions()
                : ((List<Number>) accessibleScopeMap.getOrDefault("regions", List.of())).stream().map(Number::longValue).toList();
        List<Long> accessibleOutlets = accessibleScopeMap == null
                ? scopeRoots.outlets()
                : ((List<Number>) accessibleScopeMap.getOrDefault("outlets", List.of())).stream().map(Number::longValue).toList();
        ScopeRoots accessibleScope = accessibleScopeMap == null
                ? scopeRoots
                : new ScopeRoots(accessibleSystem, accessibleRegions, accessibleOutlets);
        Number authTime = jwt.getClaim("auth_time");
        Number policyVersion = jwt.getClaim("policy_version");
        Number scopeVersion = jwt.getClaim("scope_version");
        String principalType = jwt.getClaimAsString("principal_type");
        String issuer = jwt.getClaimAsString("iss");
        Object audienceClaim = jwt.getClaim("aud");
        List<String> audience;
        if (audienceClaim instanceof List<?> values) {
            audience = values.stream().map(String::valueOf).toList();
        } else if (audienceClaim instanceof String value) {
            audience = List.of(value);
        } else {
            audience = List.of();
        }

        return new FernJwtClaims(
                userId == null ? null : userId.longValue(),
                jwt.getSubject(),
                Set.copyOf(roles == null ? List.of() : roles),
                Set.copyOf(permissions == null ? List.of() : permissions),
                scopeRoots,
                accessibleScope,
                policyVersion == null ? 0L : policyVersion.longValue(),
                scopeVersion == null ? 0L : scopeVersion.longValue(),
                jwt.getId(),
                authTime == null ? Instant.now() : Instant.ofEpochSecond(authTime.longValue()),
                jwt.getExpiresAt(),
                principalType == null ? FernPrincipalType.USER : FernPrincipalType.valueOf(principalType),
                issuer,
                Set.copyOf(audience == null ? List.of() : audience)
        );
    }
}
