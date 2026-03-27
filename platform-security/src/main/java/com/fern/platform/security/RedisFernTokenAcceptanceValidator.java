package com.fern.platform.security;

import com.fern.platform.common.UnauthorizedException;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisFernTokenAcceptanceValidator implements FernTokenAcceptanceValidator {
    private final StringRedisTemplate redisTemplate;

    public RedisFernTokenAcceptanceValidator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void validate(FernJwtClaims claims) {
        boolean blacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(FernTokenAcceptanceRules.BLACKLIST_PREFIX + claims.jti()));
        long currentPolicyVersion = readVersion(FernTokenAcceptanceRules.POLICY_VERSION_KEY, claims.policyVersion());
        long currentScopeVersion = readVersion(FernTokenAcceptanceRules.SCOPE_VERSION_KEY, claims.scopeVersion());
        if (!FernTokenAcceptanceRules.isAccepted(claims, blacklisted, currentPolicyVersion, currentScopeVersion)) {
            throw new UnauthorizedException("Token is revoked or stale");
        }
    }

    private long readVersion(String key, long fallback) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? fallback : Long.parseLong(value);
    }
}
