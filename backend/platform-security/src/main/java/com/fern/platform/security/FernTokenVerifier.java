package com.fern.platform.security;

import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.Metrics;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class FernTokenVerifier {
    private final FernJwtProperties properties;
    private final FernJwksCache jwksCache;
    private final JwtDecoder legacyDecoder;
    private final Map<String, JwtDecoder> rsaDecoders = new ConcurrentHashMap<>();

    public FernTokenVerifier(FernJwtProperties properties, FernJwksCache jwksCache) {
        this.properties = properties;
        this.jwksCache = jwksCache;
        String legacySecret = properties.resolvedLegacySecret();
        if (legacySecret == null || legacySecret.isBlank()) {
            this.legacyDecoder = null;
        } else {
            SecretKeySpec key = new SecretKeySpec(legacySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            this.legacyDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        }
    }

    public FernJwtClaims decode(String token) throws JwtException {
        SignedJWT signedJwt = parse(token);
        String algorithm = signedJwt.getHeader().getAlgorithm() == null
                ? null
                : signedJwt.getHeader().getAlgorithm().getName();
        if ("HS256".equals(algorithm)) {
            return decodeLegacy(token);
        }
        if (!"RS256".equals(algorithm)) {
            throw new JwtException("Unsupported JWT algorithm");
        }
        String kid = signedJwt.getHeader().getKeyID();
        RSAPublicKey publicKey = jwksCache.resolve(kid);
        if (publicKey == null) {
            throw new JwtException("Unknown JWT key id");
        }
        JwtDecoder decoder = rsaDecoders.computeIfAbsent(kid, ignored ->
                NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build());
        Jwt jwt = decoder.decode(token);
        return FernJwtClaims.fromMap(jwt);
    }

    private FernJwtClaims decodeLegacy(String token) {
        if (!properties.getLegacy().isEnabled() || legacyDecoder == null) {
            throw new JwtException("Legacy JWT tokens are disabled");
        }
        Jwt jwt = legacyDecoder.decode(token);
        Metrics.counter("fern_security_legacy_token_accepted").increment();
        return FernJwtClaims.fromMap(jwt);
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (Exception exception) {
            throw new JwtException("Invalid bearer token", exception);
        }
    }
}
