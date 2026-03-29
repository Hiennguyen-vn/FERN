package com.fern.platform.security;

import com.fern.platform.common.UnauthorizedException;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisFernTokenAcceptanceValidator implements FernTokenAcceptanceValidator {
    private final StringRedisTemplate redisTemplate;
    private final String currentServiceName;
    private final String expectedUserIssuer;
    private final FernTokenAcceptanceKnowledge knowledge;

    public RedisFernTokenAcceptanceValidator(StringRedisTemplate redisTemplate) {
        this(redisTemplate, "unknown-service", FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER, new InMemoryFernTokenAcceptanceKnowledge());
    }

    public RedisFernTokenAcceptanceValidator(StringRedisTemplate redisTemplate, String currentServiceName, String expectedUserIssuer) {
        this(redisTemplate, currentServiceName, expectedUserIssuer, new InMemoryFernTokenAcceptanceKnowledge());
    }

    public RedisFernTokenAcceptanceValidator(
            StringRedisTemplate redisTemplate,
            String currentServiceName,
            String expectedUserIssuer,
            FernTokenAcceptanceKnowledge knowledge
    ) {
        this.redisTemplate = redisTemplate;
        this.currentServiceName = currentServiceName;
        this.expectedUserIssuer = expectedUserIssuer;
        this.knowledge = knowledge == null ? FernTokenAcceptanceKnowledge.noop() : knowledge;
    }

    @Override
    public void validate(FernJwtClaims claims) {
        FernJwtClaimValidationRules.validateIssuerAndAudience(claims, currentServiceName, expectedUserIssuer);
        boolean blacklisted = Boolean.TRUE.equals(redisTemplate.hasKey(FernTokenAcceptanceRules.BLACKLIST_PREFIX + claims.jti()));
        long currentPolicyVersion = readVersion(FernTokenAcceptanceRules.POLICY_VERSION_KEY, claims.policyVersion());
        long currentScopeVersion = readVersion(FernTokenAcceptanceRules.SCOPE_VERSION_KEY, claims.scopeVersion());
        if (!FernTokenAcceptanceRules.isAccepted(claims, blacklisted, currentPolicyVersion, currentScopeVersion, knowledge)) {
            throw new UnauthorizedException("Token is revoked or stale");
        }
    }

    private long readVersion(String key, long fallback) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? fallback : Long.parseLong(value);
    }
}
