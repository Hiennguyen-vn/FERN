package com.fern.platform.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.security.oauth2.jwt.JwtException;

final class FernJwtKeyMaterial {
    private final String keyId;
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    private FernJwtKeyMaterial(String keyId, RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        this.keyId = keyId;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    static FernJwtKeyMaterial fromProperties(
            FernJwtProperties.KeyRing keyRing,
            String fallbackSecret,
            String purpose,
            boolean allowInsecureDefaultSecret
    ) {
        String keyId = keyRing.getKeyId() == null || keyRing.getKeyId().isBlank()
                ? purpose + "-rs256-v1"
                : keyRing.getKeyId();
        try {
            if (hasPemPair(keyRing)) {
                return new FernJwtKeyMaterial(
                        keyId,
                        parsePublicKey(keyRing.getPublicKeyPem()),
                        parsePrivateKey(keyRing.getPrivateKeyPem())
                );
            }
            if (fallbackSecret == null || fallbackSecret.isBlank()) {
                throw new IllegalStateException("FERN_JWT_SECRET or fern.security.jwt.<ring>.private-key-pem/public-key-pem must be configured");
            }
            if (FernJwtProperties.INSECURE_DEFAULT_SECRET.equals(fallbackSecret) && !allowInsecureDefaultSecret) {
                throw new IllegalStateException("FERN_JWT_SECRET must be overridden outside controlled local/test environments");
            }
            if (fallbackSecret.length() < 32 && !allowInsecureDefaultSecret) {
                throw new IllegalStateException("FERN_JWT_SECRET must be at least 32 characters");
            }
            return deterministic(keyId, fallbackSecret, purpose);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize JWT key material for " + purpose, exception);
        }
    }

    String keyId() {
        return keyId;
    }

    RSAPublicKey publicKey() {
        return publicKey;
    }

    RSAPrivateKey privateKey() {
        return privateKey;
    }

    RSAKey toPublicJwk() {
        return new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .build();
    }

    RSAKey toPrivateJwk() {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
    }

    private static boolean hasPemPair(FernJwtProperties.KeyRing keyRing) {
        return keyRing.getPrivateKeyPem() != null
                && !keyRing.getPrivateKeyPem().isBlank()
                && keyRing.getPublicKeyPem() != null
                && !keyRing.getPublicKeyPem().isBlank();
    }

    private static FernJwtKeyMaterial deterministic(String keyId, String seedSecret, String purpose) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] seed = digest.digest((seedSecret + ":" + purpose).getBytes(StandardCharsets.UTF_8));
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(seed);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        KeyPair keyPair = generator.generateKeyPair();
        return new FernJwtKeyMaterial(
                keyId,
                requireRsaPublic(keyPair.getPublic()),
                requireRsaPrivate(keyPair.getPrivate())
        );
    }

    private static RSAPublicKey parsePublicKey(String pem) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(sanitizePem(pem));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(spec);
        return requireRsaPublic(key);
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(sanitizePem(pem));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(spec);
        return requireRsaPrivate(key);
    }

    private static String sanitizePem(String pem) {
        return pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private static RSAPublicKey requireRsaPublic(PublicKey key) {
        if (key instanceof RSAPublicKey rsaPublicKey) {
            return rsaPublicKey;
        }
        throw new JwtException("Configured JWT public key is not RSA");
    }

    private static RSAPrivateKey requireRsaPrivate(PrivateKey key) {
        if (key instanceof RSAPrivateKey rsaPrivateKey) {
            return rsaPrivateKey;
        }
        throw new JwtException("Configured JWT private key is not RSA");
    }
}
