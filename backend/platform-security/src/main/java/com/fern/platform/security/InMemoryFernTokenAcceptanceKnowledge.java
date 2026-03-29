package com.fern.platform.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryFernTokenAcceptanceKnowledge implements FernTokenAcceptanceKnowledge {
    private final AtomicLong minimumPolicyVersion = new AtomicLong();
    private final AtomicLong minimumScopeVersion = new AtomicLong();
    private final Set<String> blacklistedJtis = ConcurrentHashMap.newKeySet();

    @Override
    public void observe(
            FernJwtClaims claims,
            boolean blacklisted,
            long currentPolicyVersion,
            long currentScopeVersion,
            boolean authoritative
    ) {
        if (blacklisted) {
            blacklistedJtis.add(claims.jti());
        }
        if (authoritative) {
            minimumPolicyVersion.set(currentPolicyVersion);
            minimumScopeVersion.set(currentScopeVersion);
        }
    }

    @Override
    public boolean isKnownBlacklisted(String jti) {
        return blacklistedJtis.contains(jti);
    }

    @Override
    public long minimumPolicyVersion() {
        return minimumPolicyVersion.get();
    }

    @Override
    public long minimumScopeVersion() {
        return minimumScopeVersion.get();
    }
}
