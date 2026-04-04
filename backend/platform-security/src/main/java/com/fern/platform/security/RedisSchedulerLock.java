package com.fern.platform.security;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Distributed scheduler lock using Redis to prevent duplicate execution of scheduled
 * tasks across multiple application instances.
 *
 * <p>Uses a simple SET NX EX pattern to acquire a lock with configurable TTL.
 * The lock auto-expires after TTL, handling the case where an instance crashes
 * mid-execution.
 *
 * <p>Falls back to allowing execution when Redis is unavailable (best-effort dedup).
 * The recovery jobs protected by this lock are idempotent by design, so duplicate
 * execution is safe — just wasteful.
 */
public class RedisSchedulerLock {
    private static final Logger log = LoggerFactory.getLogger(RedisSchedulerLock.class);

    private static final String LOCK_PREFIX = "fern:scheduler:lock:";

    private static final DefaultRedisScript<Boolean> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
                return true
            else
                return false
            end
            """,
            Boolean.class
    );

    private static final DefaultRedisScript<Boolean> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1]) == 1
            else
                return false
            end
            """,
            Boolean.class
    );

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    /**
     * @param redisTemplate the Redis connection
     * @param instanceId unique identifier for this instance (e.g. hostname, container ID)
     */
    public RedisSchedulerLock(StringRedisTemplate redisTemplate, String instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    /**
     * Attempts to acquire the named lock.
     *
     * @param lockName logical name of the scheduled task
     * @param ttl maximum duration the lock is held (safety timeout)
     * @return true if lock acquired, false if another instance holds it
     */
    public boolean tryAcquire(String lockName, Duration ttl) {
        try {
            Boolean acquired = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(LOCK_PREFIX + lockName),
                    instanceId,
                    String.valueOf(ttl.toSeconds())
            );
            return Boolean.TRUE.equals(acquired);
        } catch (Exception exception) {
            log.warn("Failed to acquire scheduler lock '{}': {}. Proceeding without lock.",
                    lockName, exception.getMessage());
            return true;
        }
    }

    /**
     * Releases the named lock if this instance is the holder.
     */
    public void release(String lockName) {
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(LOCK_PREFIX + lockName),
                    instanceId
            );
        } catch (Exception exception) {
            log.warn("Failed to release scheduler lock '{}': {}", lockName, exception.getMessage());
        }
    }
}
