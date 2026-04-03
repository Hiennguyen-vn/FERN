package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.jwt.JwtException;
import com.nimbusds.jose.jwk.JWKSet;

public class FernJwtService {
    private final FernJwtProperties properties;
    private final FernTokenIssuer tokenIssuer;
    private final FernTokenVerifier tokenVerifier;
    private final FernJwksProvider jwksProvider;

    public FernJwtService(FernJwtProperties properties, Clock clock) {
        this.properties = properties;
        FernJwtKeyMaterial userKeyMaterial = FernJwtKeyMaterial.fromProperties(
                properties.getUser(),
                properties.resolvedLegacySecret(),
                "user",
                properties.isAllowInsecureDefaultSecret()
        );
        FernJwtKeyMaterial gatewayKeyMaterial = FernJwtKeyMaterial.fromProperties(
                properties.getGateway(),
                properties.resolvedLegacySecret(),
                "gateway",
                properties.isAllowInsecureDefaultSecret()
        );
        FernJwtKeyMaterial serviceKeyMaterial = FernJwtKeyMaterial.fromProperties(
                properties.getService(),
                properties.resolvedLegacySecret(),
                "service",
                properties.isAllowInsecureDefaultSecret()
        );
        this.tokenIssuer = new FernTokenIssuer(properties, clock, userKeyMaterial, gatewayKeyMaterial, serviceKeyMaterial);
        this.jwksProvider = new FernJwksProvider(tokenIssuer.publicJwkSet());
        this.tokenVerifier = new FernTokenVerifier(
                properties,
                new FernJwksCache(
                        jwksProvider,
                        clock,
                        Duration.ofSeconds(properties.getJwks().getRefreshIntervalSeconds())
                )
        );
    }

    public String encode(FernJwtClaims claims, Duration ttl) {
        return tokenIssuer.encode(claims, ttl);
    }

    public FernJwtClaims decode(String token) throws JwtException {
        return tokenVerifier.decode(token);
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

    public JWKSet currentJwkSet() {
        return jwksProvider.currentJwkSet();
    }

    public FernJwksProvider jwksProvider() {
        return jwksProvider;
    }
}
