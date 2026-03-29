package com.fern.platform.security;

public final class FernTokenAcceptanceRules {
    public static final String POLICY_VERSION_KEY = "fern:versions:policy";
    public static final String SCOPE_VERSION_KEY = "fern:versions:scope";
    public static final String BLACKLIST_PREFIX = "fern:iam:blacklist:";

    private FernTokenAcceptanceRules() {
    }

    public static boolean isAccepted(
            FernJwtClaims claims,
            boolean blacklisted,
            long currentPolicyVersion,
            long currentScopeVersion
    ) {
        return isAccepted(claims, blacklisted, currentPolicyVersion, currentScopeVersion, FernTokenAcceptanceKnowledge.noop());
    }

    public static boolean isAccepted(
            FernJwtClaims claims,
            boolean blacklisted,
            long currentPolicyVersion,
            long currentScopeVersion,
            FernTokenAcceptanceKnowledge knowledge
    ) {
        return isAccepted(claims, blacklisted, currentPolicyVersion, currentScopeVersion, knowledge, true);
    }

    public static boolean isAccepted(
            FernJwtClaims claims,
            boolean blacklisted,
            long currentPolicyVersion,
            long currentScopeVersion,
            FernTokenAcceptanceKnowledge knowledge,
            boolean authoritativeObservation
    ) {
        FernTokenAcceptanceKnowledge effectiveKnowledge = knowledge == null
                ? FernTokenAcceptanceKnowledge.noop()
                : knowledge;
        effectiveKnowledge.observe(claims, blacklisted, currentPolicyVersion, currentScopeVersion, authoritativeObservation);
        if (blacklisted || effectiveKnowledge.isKnownBlacklisted(claims.jti())) {
            return false;
        }
        long minimumPolicyVersion = Math.max(currentPolicyVersion, effectiveKnowledge.minimumPolicyVersion());
        long minimumScopeVersion = Math.max(currentScopeVersion, effectiveKnowledge.minimumScopeVersion());
        return minimumPolicyVersion <= claims.policyVersion()
                && minimumScopeVersion <= claims.scopeVersion();
    }
}
