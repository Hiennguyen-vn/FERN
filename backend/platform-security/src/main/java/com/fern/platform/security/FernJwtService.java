package com.fern.platform.security;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtException;
import com.nimbusds.jose.jwk.JWKSet;

public class FernJwtService {
    private static final Logger log = LoggerFactory.getLogger(FernJwtService.class);

    private final FernJwtProperties properties;
    private final FernTokenIssuer tokenIssuer;
    private final FernTokenVerifier tokenVerifier;
    private final FernJwksProvider jwksProvider;

    public FernJwtService(FernJwtProperties properties, Clock clock) {
        this.properties = properties;
        warnIfUsingSharedSecretFallback(properties);
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

    /**
     * Emits a startup warning when any key ring falls back to the shared FERN_JWT_SECRET
     * instead of dedicated PEM keypairs.
     *
     * <p>In this fallback mode all three key rings (user / gateway / service) are derived
     * deterministically from the same secret — they are namespaced so the resulting RSA keys
     * are mathematically distinct, but a single secret compromise exposes all three rings at
     * once.  Production deployments MUST supply per-ring PEM keypairs via:
     * {@code fern.security.jwt.user.private-key-pem / public-key-pem},
     * {@code fern.security.jwt.gateway.*}, and {@code fern.security.jwt.service.*}.
     */
    private static void warnIfUsingSharedSecretFallback(FernJwtProperties properties) {
        boolean userHasPem = hasPem(properties.getUser());
        boolean gatewayHasPem = hasPem(properties.getGateway());
        boolean serviceHasPem = hasPem(properties.getService());
        if (!userHasPem || !gatewayHasPem || !serviceHasPem) {
            log.warn(
                "[SECURITY] One or more JWT key rings are using the shared FERN_JWT_SECRET fallback "
                + "(user.pem={}, gateway.pem={}, service.pem={}). "
                + "Configure dedicated PEM keypairs for each ring in production to limit blast radius "
                + "if the shared secret is ever compromised.",
                userHasPem, gatewayHasPem, serviceHasPem
            );
        }
    }

    private static boolean hasPem(FernJwtProperties.KeyRing keyRing) {
        return keyRing.getPrivateKeyPem() != null && !keyRing.getPrivateKeyPem().isBlank()
                && keyRing.getPublicKeyPem() != null && !keyRing.getPublicKeyPem().isBlank();
    }
}
