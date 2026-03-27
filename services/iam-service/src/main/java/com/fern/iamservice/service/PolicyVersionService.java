package com.fern.iamservice.service;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyVersionService {
    public static final String REDIS_KEY = "fern:versions:policy";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public PolicyVersionService(NamedParameterJdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public long currentVersion() {
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        Long dbValue = jdbcTemplate.queryForObject("SELECT version FROM iam.policy_version_state WHERE id = 1", Map.of(), Long.class);
        long resolved = dbValue == null ? 1L : dbValue;
        redisTemplate.opsForValue().set(REDIS_KEY, Long.toString(resolved));
        return resolved;
    }

    @Transactional
    public long bump() {
        long dbValue = jdbcTemplate.queryForObject("SELECT version FROM iam.policy_version_state WHERE id = 1", Map.of(), Long.class);
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        long next = Math.max(dbValue, cached == null ? 0L : Long.parseLong(cached)) + 1;
        jdbcTemplate.update("""
                UPDATE iam.policy_version_state
                SET version = :version, updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """, Map.of("version", next));
        redisTemplate.opsForValue().set(REDIS_KEY, Long.toString(next));
        return next;
    }

    @Transactional
    public void ensureRedisMirror() {
        long dbValue = jdbcTemplate.queryForObject("SELECT version FROM iam.policy_version_state WHERE id = 1", Map.of(), Long.class);
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached == null || Long.parseLong(cached) < dbValue) {
            redisTemplate.opsForValue().set(REDIS_KEY, Long.toString(dbValue));
        }
    }
}
