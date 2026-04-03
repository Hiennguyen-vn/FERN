package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet.Builder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

public class FernTokenIssuer {
    private final FernJwtProperties properties;
    private final Clock clock;
    private final FernJwtKeyMaterial userKeyMaterial;
    private final FernJwtKeyMaterial gatewayKeyMaterial;
    private final FernJwtKeyMaterial serviceKeyMaterial;
    private final JwtEncoder userEncoder;
    private final JwtEncoder gatewayEncoder;
    private final JwtEncoder serviceEncoder;

    public FernTokenIssuer(
            FernJwtProperties properties,
            Clock clock,
            FernJwtKeyMaterial userKeyMaterial,
            FernJwtKeyMaterial gatewayKeyMaterial,
            FernJwtKeyMaterial serviceKeyMaterial
    ) {
        this.properties = properties;
        this.clock = clock;
        this.userKeyMaterial = userKeyMaterial;
        this.gatewayKeyMaterial = gatewayKeyMaterial;
        this.serviceKeyMaterial = serviceKeyMaterial;
        this.userEncoder = createEncoder(userKeyMaterial);
        this.gatewayEncoder = createEncoder(gatewayKeyMaterial);
        this.serviceEncoder = createEncoder(serviceKeyMaterial);
    }

    public String encode(FernJwtClaims claims, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String issuer = resolveIssuer(claims);
        Set<String> audience = resolveAudience(claims);

        Map<String, Object> scopeRoots = new HashMap<>();
        scopeRoots.put("system", claims.scopeRoots().system());
        scopeRoots.put("regions", claims.scopeRoots().regions());
        scopeRoots.put("outlets", claims.scopeRoots().outlets());
        Map<String, Object> accessibleScope = new HashMap<>();
        accessibleScope.put("system", claims.accessibleScope().system());
        accessibleScope.put("regions", claims.accessibleScope().regions());
        accessibleScope.put("outlets", claims.accessibleScope().outlets());

        Builder builder = JwtClaimsSet.builder()
                .subject(claims.username())
                .id(claims.jti())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .issuer(issuer)
                .audience(audience.stream().toList())
                .claim("roles", claims.roles())
                .claim("permissions", claims.permissions())
                .claim("scope_roots", scopeRoots)
                .claim("accessible_scope", accessibleScope)
                .claim("policy_version", claims.policyVersion())
                .claim("scope_version", claims.scopeVersion())
                .claim("auth_time", claims.authTime().getEpochSecond())
                .claim("principal_type", claims.principalType().name());

        if (claims.userId() != null) {
            builder.claim("user_id", claims.userId());
        }

        FernJwtKeyMaterial keyMaterial = selectKeyMaterial(claims, issuer);
        JwtEncoder encoder = selectEncoder(keyMaterial);
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyMaterial.keyId()).build(),
                builder.build()
        )).getTokenValue();
    }

    JWKSet publicJwkSet() {
        return new JWKSet(java.util.List.of(
                userKeyMaterial.toPublicJwk(),
                gatewayKeyMaterial.toPublicJwk(),
                serviceKeyMaterial.toPublicJwk()
        ));
    }

    private JwtEncoder createEncoder(FernJwtKeyMaterial keyMaterial) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(keyMaterial.toPrivateJwk())));
    }

    private JwtEncoder selectEncoder(FernJwtKeyMaterial keyMaterial) {
        if (keyMaterial == userKeyMaterial) {
            return userEncoder;
        }
        if (keyMaterial == gatewayKeyMaterial) {
            return gatewayEncoder;
        }
        if (keyMaterial == serviceKeyMaterial) {
            return serviceEncoder;
        }
        throw new JwtException("No encoder available for selected key material");
    }

    private FernJwtKeyMaterial selectKeyMaterial(FernJwtClaims claims, String issuer) {
        if (claims.principalType() == FernPrincipalType.SERVICE) {
            return serviceKeyMaterial;
        }
        if (properties.getGatewayRelayUserIssuer().equals(issuer)) {
            return gatewayKeyMaterial;
        }
        return userKeyMaterial;
    }

    private String resolveIssuer(FernJwtClaims claims) {
        if (claims.issuer() != null && !claims.issuer().isBlank()) {
            return claims.issuer();
        }
        if (claims.principalType() == FernPrincipalType.SERVICE) {
            return claims.username();
        }
        return properties.getUserTokenIssuer();
    }

    private Set<String> resolveAudience(FernJwtClaims claims) {
        if (claims.audience() != null && !claims.audience().isEmpty()) {
            return claims.audience();
        }
        if (claims.principalType() == FernPrincipalType.SERVICE) {
            return Set.of(claims.username());
        }
        return Set.copyOf(new LinkedHashSet<>(properties.getUserTokenAudiences()));
    }
}
