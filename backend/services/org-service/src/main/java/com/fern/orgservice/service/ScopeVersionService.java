package com.fern.orgservice.service;

import java.time.Clock;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScopeVersionService {
    public static final String REDIS_KEY = "fern:versions:scope";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public ScopeVersionService(NamedParameterJdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public long currentVersion() {
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        Long dbValue = jdbcTemplate.queryForObject("SELECT version FROM org.scope_version_state WHERE id = 1", Map.of(), Long.class);
        long resolved = dbValue == null ? 1L : dbValue;
        redisTemplate.opsForValue().set(REDIS_KEY, Long.toString(resolved));
        return resolved;
    }

    @Transactional
    public long bump() {
        Long next = jdbcTemplate.queryForObject("""
                UPDATE org.scope_version_state
                SET version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                RETURNING version
                """, Map.of(), Long.class);
        long resolved = next == null ? currentVersion() + 1 : next;
        redisTemplate.opsForValue().set(REDIS_KEY, Long.toString(resolved));
        return resolved;
    }

    @Transactional
    public void ensureRedisMirror() {
        long dbValue = jdbcTemplate.queryForObject("SELECT version FROM org.scope_version_state WHERE id = 1", Map.of(), Long.class);
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached == null || Long.parseLong(cached) < dbValue) {
            redisTemplate.opsForValue().set(REDIS_KEY, Long.toString(dbValue));
        }
    }
}
