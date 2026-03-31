package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.UnauthorizedException;

public final class FernJwtClaimValidationRules {
    private FernJwtClaimValidationRules() {
    }

    public static void validateGatewayIngress(FernJwtClaims claims, String currentServiceName, String expectedPublicUserIssuer) {
        validateAudience(claims, currentServiceName);
        if (claims.principalType() == FernPrincipalType.USER) {
            if (!expectedPublicUserIssuer.equals(claims.issuer())) {
                throw new UnauthorizedException("Invalid bearer token");
            }
            return;
        }
        validateServiceIssuer(claims);
    }

    public static void validateDownstreamIngress(FernJwtClaims claims, String currentServiceName, String expectedGatewayRelayUserIssuer) {
        validateAudience(claims, currentServiceName);
        if (claims.principalType() == FernPrincipalType.USER) {
            if (!expectedGatewayRelayUserIssuer.equals(claims.issuer())) {
                throw new UnauthorizedException("Invalid bearer token");
            }
            return;
        }
        validateServiceIssuer(claims);
    }

    private static void validateAudience(FernJwtClaims claims, String currentServiceName) {
        if (claims.issuer() == null || claims.issuer().isBlank()) {
            throw new UnauthorizedException("Invalid bearer token");
        }
        if (claims.audience() == null || claims.audience().isEmpty() || !claims.audience().contains(currentServiceName)) {
            throw new UnauthorizedException("Invalid bearer token");
        }
    }

    private static void validateServiceIssuer(FernJwtClaims claims) {
        if (!claims.username().equals(claims.issuer())) {
            throw new UnauthorizedException("Invalid bearer token");
        }
    }
}
