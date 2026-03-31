package com.fern.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.common.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FernJwtClaimValidationRulesTest {
    @Test
    void shouldAcceptGatewayFacingUserTokenFromIamIssuer() {
        FernJwtClaims claims = userClaims(
                FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER,
                Set.of("api-gateway")
        );

        assertThatCode(() -> FernJwtClaimValidationRules.validateGatewayIngress(
                claims,
                "api-gateway",
                FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDirectIamIssuedUserTokenAtDownstream() {
        FernJwtClaims claims = userClaims(
                FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER,
                Set.of("finance-service")
        );

        assertThatThrownBy(() -> FernJwtClaimValidationRules.validateDownstreamIngress(
                claims,
                "finance-service",
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid bearer token");
    }

    @Test
    void shouldAcceptGatewayRelayedUserTokenAtDownstream() {
        FernJwtClaims claims = userClaims(
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("finance-service")
        );

        assertThatCode(() -> FernJwtClaimValidationRules.validateDownstreamIngress(
                claims,
                "finance-service",
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptServiceTokenWhenIssuerMatchesUsernameAndAudienceContainsTargetService() {
        FernJwtClaims claims = serviceClaims("inventory-service", "inventory-service", Set.of("org-service", "audit-service"));

        assertThatCode(() -> FernJwtClaimValidationRules.validateDownstreamIngress(
                claims,
                "org-service",
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectServiceTokenWhenAudienceDoesNotContainTargetService() {
        FernJwtClaims claims = serviceClaims("inventory-service", "inventory-service", Set.of("inventory-service"));

        assertThatThrownBy(() -> FernJwtClaimValidationRules.validateDownstreamIngress(
                claims,
                "org-service",
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid bearer token");
    }

    @Test
    void shouldRejectServiceTokenWhenIssuerDoesNotMatchUsername() {
        FernJwtClaims claims = serviceClaims("inventory-service", "org-internal", Set.of("org-service"));

        assertThatThrownBy(() -> FernJwtClaimValidationRules.validateDownstreamIngress(
                claims,
                "org-service",
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER
        )).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid bearer token");
    }

    private FernJwtClaims userClaims(String issuer, Set<String> audience) {
        Instant now = Instant.parse("2026-03-29T00:00:00Z");
        return new FernJwtClaims(
                1L,
                "finance-user",
                Set.of("finance"),
                Set.of("finance.payroll.read"),
                new ScopeRoots(false, List.of(1L), List.of()),
                1L,
                1L,
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(300),
                FernPrincipalType.USER,
                issuer,
                audience
        );
    }

    private FernJwtClaims serviceClaims(String username, String issuer, Set<String> audience) {
        Instant now = Instant.parse("2026-03-29T00:00:00Z");
        return new FernJwtClaims(
                null,
                username,
                Set.of(),
                Set.of("org.scope.resolve"),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(300),
                FernPrincipalType.SERVICE,
                issuer,
                audience
        );
    }
}
