package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;

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
        if (blacklisted) {
            return false;
        }
        if (claims.principalType() == FernPrincipalType.SERVICE) {
            return true;
        }
        return currentPolicyVersion <= claims.policyVersion()
                && currentScopeVersion <= claims.scopeVersion();
    }
}
