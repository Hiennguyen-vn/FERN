package com.fern.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
}
