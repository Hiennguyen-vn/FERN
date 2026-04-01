package com.fern.platform.security;

import com.fern.platform.common.FernPrincipal;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FernTokenVersionGraceService {
    static final String VERSION_GRACE_PREFIX = "fern:iam:version-grace:";
    private static final Duration DEFAULT_GRACE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final Duration graceTtl;

    public FernTokenVersionGraceService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_GRACE_TTL);
    }

    FernTokenVersionGraceService(StringRedisTemplate redisTemplate, Duration graceTtl) {
        this.redisTemplate = redisTemplate;
        this.graceTtl = graceTtl == null || graceTtl.isNegative() || graceTtl.isZero() ? DEFAULT_GRACE_TTL : graceTtl;
    }

    public void grant(FernPrincipal principal) {
        if (principal == null || principal.isService() || principal.jti() == null || principal.jti().isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(key(principal.jti()), "1", graceTtl);
    }

    public boolean isActive(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    public static String key(String jti) {
        return VERSION_GRACE_PREFIX + jti;
    }
}
