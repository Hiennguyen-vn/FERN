package com.fern.platform.security;

public interface FernTokenAcceptanceValidator {
    void validate(FernJwtClaims claims);

    static FernTokenAcceptanceValidator noop() {
        return claims -> {
        };
    }
}
