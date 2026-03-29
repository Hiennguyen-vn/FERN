package com.fern.iamservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginProtectionService {
    private static final long MAX_ATTEMPTS = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public LoginProtectionService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public boolean isTemporarilyLocked(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(username)));
    }

    public Instant lockedUntil(String username) {
        Long seconds = redisTemplate.getExpire(lockKey(username));
        if (seconds == null || seconds <= 0) {
            return null;
        }
        return clock.instant().plusSeconds(seconds);
    }

    public boolean recordFailure(String username) {
        Long count = redisTemplate.opsForValue().increment(failureKey(username));
        if (count != null && count == 1) {
            redisTemplate.expire(failureKey(username), FAILURE_WINDOW);
        }
        if (count != null && count >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockKey(username), "1", LOCKOUT_DURATION);
            redisTemplate.delete(failureKey(username));
            return true;
        }
        return false;
    }

    public void clearFailures(String username) {
        redisTemplate.delete(failureKey(username));
        redisTemplate.delete(lockKey(username));
    }

    private String failureKey(String username) {
        return "fern:iam:login-fail:" + username;
    }

    private String lockKey(String username) {
        return "fern:iam:login-lock:" + username;
    }
}
