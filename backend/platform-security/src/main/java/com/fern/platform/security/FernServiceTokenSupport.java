package com.fern.platform.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ScopeRoots;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

public class FernServiceTokenSupport {
    private final FernJwtService jwtService;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;

    public FernServiceTokenSupport(FernJwtService jwtService, Clock clock, StringRedisTemplate redisTemplate) {
        this.jwtService = jwtService;
        this.clock = clock;
        this.redisTemplate = redisTemplate;
    }

    public String issueToken(String serviceName, Collection<String> permissions) {
        return issueToken(serviceName, serviceName, permissions);
    }

    public String issueToken(String serviceName, String audience, Collection<String> permissions) {
        Instant now = clock.instant();
        return jwtService.encode(
                new FernJwtClaims(
                        null,
                        serviceName,
                        Set.of(),
                        Set.copyOf(permissions),
                        new ScopeRoots(true, List.of(), List.of()),
                        readVersion(FernTokenAcceptanceRules.POLICY_VERSION_KEY),
                        readVersion(FernTokenAcceptanceRules.SCOPE_VERSION_KEY),
                        UUID.randomUUID().toString(),
                        now,
                        now.plus(jwtService.serviceTokenTtl()),
                        FernPrincipalType.SERVICE,
                        serviceName,
                        Set.of(audience)
                ),
                jwtService.serviceTokenTtl()
        );
    }

    private long readVersion(String key) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Redis is required for service token issuance");
        }
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }
}
