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
     * Enforces that all three JWT key rings use dedicated PEM keypairs when
     * {@code fern.security.jwt.allow-insecure-default-secret} is {@code false} (the default
     * for production).  In shared-secret fallback mode all three rings are derived
     * deterministically from the same secret — a single secret compromise exposes all rings
     * simultaneously.
     *
     * <p>Local/test environments may set {@code allow-insecure-default-secret: true} to
     * bypass this check and use the built-in default secret.
     *
     * @throws IllegalStateException if any ring is missing PEM keypairs and the insecure
     *     default secret is not explicitly allowed
     */
    private static void warnIfUsingSharedSecretFallback(FernJwtProperties properties) {
        boolean userHasPem = hasPem(properties.getUser());
        boolean gatewayHasPem = hasPem(properties.getGateway());
        boolean serviceHasPem = hasPem(properties.getService());
        if (!userHasPem || !gatewayHasPem || !serviceHasPem) {
            if (!properties.isAllowInsecureDefaultSecret()) {
                throw new IllegalStateException(
                    "[SECURITY] All JWT key rings must use dedicated PEM keypairs in production. "
                    + "Missing PEM for: "
                    + (!userHasPem ? "user " : "")
                    + (!gatewayHasPem ? "gateway " : "")
                    + (!serviceHasPem ? "service" : "")
                    + ". Configure fern.security.jwt.{user,gateway,service}.private-key-pem and "
                    + "public-key-pem, or set fern.security.jwt.allow-insecure-default-secret=true "
                    + "for local/test environments only."
                );
            }
            log.warn(
                "[SECURITY] One or more JWT key rings are using the shared FERN_JWT_SECRET fallback "
                + "(user.pem={}, gateway.pem={}, service.pem={}). "
                + "This is only acceptable in local/test environments. "
                + "Configure dedicated PEM keypairs for each ring in production.",
                userHasPem, gatewayHasPem, serviceHasPem
            );
        }
    }

    private static boolean hasPem(FernJwtProperties.KeyRing keyRing) {
        return keyRing.getPrivateKeyPem() != null && !keyRing.getPrivateKeyPem().isBlank()
                && keyRing.getPublicKeyPem() != null && !keyRing.getPublicKeyPem().isBlank();
    }
}
