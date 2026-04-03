package com.fern.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class FernJwtServiceTest {
    @Test
    void shouldDefaultUserTokensToGatewayAudienceOnly() {
        Instant now = Instant.parse("2036-03-31T11:00:00Z");
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret(FernJwtProperties.INSECURE_DEFAULT_SECRET);
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        String token = jwtService.encode(
                new FernJwtClaims(
                        1L,
                        "gateway-user",
                        Set.of("staff"),
                        Set.of("org.region.read"),
                        new com.fern.platform.common.ScopeRoots(false, List.of(1L), List.of()),
                        1L,
                        1L,
                        "gateway-audience-jti",
                        now,
                        now.plus(jwtService.accessTokenTtl())
                ),
                jwtService.accessTokenTtl()
        );

        FernJwtClaims claims = jwtService.decode(token);
        assertThat(claims.issuer()).isEqualTo(FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER);
        assertThat(claims.audience()).containsExactly("api-gateway");
    }

    @Test
    void shouldIssueRs256TokenWithKidHeader() throws Exception {
        Instant now = Instant.parse("2036-03-31T11:00:00Z");
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret(FernJwtProperties.INSECURE_DEFAULT_SECRET);
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.fixed(now, ZoneOffset.UTC));

        String token = jwtService.encode(
                new FernJwtClaims(
                        7L,
                        "ops-user",
                        Set.of("staff"),
                        Set.of("report.read"),
                        new com.fern.platform.common.ScopeRoots(false, List.of(1L), List.of(101L)),
                        2L,
                        3L,
                        "kid-check-jti",
                        now,
                        now.plus(jwtService.accessTokenTtl())
                ),
                jwtService.accessTokenTtl()
        );

        SignedJWT signedJwt = SignedJWT.parse(token);
        assertThat(signedJwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(signedJwt.getHeader().getKeyID()).isEqualTo(properties.getUser().getKeyId());
        FernJwtClaims claims = jwtService.decode(token);
        assertThat(claims.username()).isEqualTo("ops-user");
    }

    @Test
    void shouldAcceptLegacyHs256TokensWhenEnabled() {
        Instant now = Instant.parse("2036-03-31T11:00:00Z");
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret(FernJwtProperties.INSECURE_DEFAULT_SECRET);
        properties.setAllowInsecureDefaultSecret(true);
        properties.getLegacy().setEnabled(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.fixed(now, ZoneOffset.UTC));

        String legacyToken = legacyToken(now, properties);

        FernJwtClaims claims = jwtService.decode(legacyToken);
        assertThat(claims.username()).isEqualTo("legacy-user");
        assertThat(claims.issuer()).isEqualTo(FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER);
    }

    @Test
    void shouldRejectLegacyHs256TokensWhenDisabled() {
        Instant now = Instant.parse("2036-03-31T11:00:00Z");
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret(FernJwtProperties.INSECURE_DEFAULT_SECRET);
        properties.setAllowInsecureDefaultSecret(true);
        properties.getLegacy().setEnabled(false);
        FernJwtService jwtService = new FernJwtService(properties, Clock.fixed(now, ZoneOffset.UTC));

        String legacyToken = legacyToken(now, properties);

        assertThatThrownBy(() -> jwtService.decode(legacyToken))
                .hasMessageContaining("Legacy JWT tokens are disabled");
    }

    private String legacyToken(Instant now, FernJwtProperties properties) {
        SecretKeySpec key = new SecretKeySpec(properties.getSecret().getBytes(), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .subject("legacy-user")
                .id("legacy-jti")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .issuer(FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER)
                .audience(List.of("api-gateway"))
                .claim("roles", List.of("staff"))
                .claim("permissions", List.of("report.read"))
                .claim("scope_roots", java.util.Map.of("system", false, "regions", List.of(1L), "outlets", List.of(101L)))
                .claim("accessible_scope", java.util.Map.of("system", false, "regions", List.of(1L), "outlets", List.of(101L)))
                .claim("policy_version", 1L)
                .claim("scope_version", 1L)
                .claim("auth_time", now.getEpochSecond())
                .claim("principal_type", "USER")
                .claim("user_id", 9L)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claimsSet)).getTokenValue();
    }
}
