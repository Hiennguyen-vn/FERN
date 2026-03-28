package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ScopeRoots;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet.Builder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

public class FernJwtService {
    private final FernJwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public FernJwtService(FernJwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        validateSecret(properties);
        SecretKeySpec key = new SecretKeySpec(properties.getSecret().getBytes(), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private void validateSecret(FernJwtProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException("FERN_JWT_SECRET must be configured");
        }
        if (FernJwtProperties.INSECURE_DEFAULT_SECRET.equals(properties.getSecret()) && !properties.isAllowInsecureDefaultSecret()) {
            throw new IllegalStateException("FERN_JWT_SECRET must be overridden outside controlled local/test environments");
        }
        if (properties.getSecret().length() < 32 && !properties.isAllowInsecureDefaultSecret()) {
            throw new IllegalStateException("FERN_JWT_SECRET must be at least 32 characters");
        }
    }

    public String encode(FernJwtClaims claims, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);

        Map<String, Object> scopeRoots = new HashMap<>();
        scopeRoots.put("system", claims.scopeRoots().system());
        scopeRoots.put("regions", claims.scopeRoots().regions());
        scopeRoots.put("outlets", claims.scopeRoots().outlets());

        Builder builder = JwtClaimsSet.builder()
                .subject(claims.username())
                .id(claims.jti())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", claims.roles())
                .claim("permissions", claims.permissions())
                .claim("scope_roots", scopeRoots)
                .claim("policy_version", claims.policyVersion())
                .claim("scope_version", claims.scopeVersion())
                .claim("auth_time", claims.authTime().getEpochSecond())
                .claim("principal_type", claims.principalType().name());

        if (claims.userId() != null) {
            builder.claim("user_id", claims.userId());
        }

        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }

    public FernJwtClaims decode(String token) throws JwtException {
        Jwt jwt = decoder.decode(token);
        return FernJwtClaims.fromMap(jwt);
    }

    public Duration accessTokenTtl() {
        return Duration.ofSeconds(properties.getAccessTokenTtlSeconds());
    }

    public Duration refreshTokenTtl() {
        return Duration.ofSeconds(properties.getRefreshTokenTtlSeconds());
    }

    public Duration serviceTokenTtl() {
        return Duration.ofSeconds(properties.getServiceTokenTtlSeconds());
    }
}
