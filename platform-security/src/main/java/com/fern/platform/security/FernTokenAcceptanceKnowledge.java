package com.fern.platform.security;

public interface FernTokenAcceptanceKnowledge {
    FernTokenAcceptanceKnowledge NOOP = new FernTokenAcceptanceKnowledge() {
    };

    default void observe(
            FernJwtClaims claims,
            boolean blacklisted,
            long currentPolicyVersion,
            long currentScopeVersion,
            boolean authoritative
    ) {
    }

    default boolean isKnownBlacklisted(String jti) {
        return false;
    }

    default long minimumPolicyVersion() {
        return 0L;
    }

    default long minimumScopeVersion() {
        return 0L;
    }

    static FernTokenAcceptanceKnowledge noop() {
        return NOOP;
    }
}
