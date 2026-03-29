package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.UnauthorizedException;

public final class FernJwtClaimValidationRules {
    private FernJwtClaimValidationRules() {
    }

    public static void validateIssuerAndAudience(FernJwtClaims claims, String currentServiceName, String expectedUserIssuer) {
        if (claims.issuer() == null || claims.issuer().isBlank()) {
            throw new UnauthorizedException("Invalid bearer token");
        }
        if (claims.audience() == null || claims.audience().isEmpty() || !claims.audience().contains(currentServiceName)) {
            throw new UnauthorizedException("Invalid bearer token");
        }
        if (claims.principalType() == FernPrincipalType.USER) {
            if (!expectedUserIssuer.equals(claims.issuer())) {
                throw new UnauthorizedException("Invalid bearer token");
            }
            return;
        }
        if (!claims.username().equals(claims.issuer())) {
            throw new UnauthorizedException("Invalid bearer token");
        }
    }
}
