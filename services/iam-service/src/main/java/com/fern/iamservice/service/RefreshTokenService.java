package com.fern.iamservice.service;

import com.fern.iamservice.domain.AuthSessionEntity;
import com.fern.iamservice.repository.AuthSessionRepository;
import com.fern.platform.common.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
public class RefreshTokenService {
    public static final String BLACKLIST_PREFIX = "fern:iam:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    public RefreshTokenService(StringRedisTemplate redisTemplate, AuthSessionRepository authSessionRepository, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.authSessionRepository = authSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public String issue(Long userId) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.setUserId(userId);
        entity.setSessionId(UUID.randomUUID().toString());
        entity.setRefreshTokenHash(sha256(token));
        entity.setIpAddress(currentIpAddress());
        entity.setUserAgent(currentUserAgent());
        entity.setIssuedAt(clock.instant());
        entity.setExpiresAt(clock.instant().plus(Duration.ofDays(7)));
        entity.setCreatedAt(clock.instant());
        entity.setUpdatedAt(clock.instant());
        authSessionRepository.save(entity);
        return token;
    }

    @Transactional
    public String rotate(String existingToken, Long userId) {
        revoke(existingToken);
        return issue(userId);
    }

    @Transactional(readOnly = true)
    public Long requireUserId(String token) {
        AuthSessionEntity session = authSessionRepository.findByRefreshTokenHash(sha256(token))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(clock.instant())) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }
        return session.getUserId();
    }

    @Transactional
    public void revoke(String token) {
        authSessionRepository.findByRefreshTokenHash(sha256(token)).ifPresent(session -> {
            session.setRevokedAt(clock.instant());
            session.setUpdatedAt(clock.instant());
        });
    }

    public void blacklistJti(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttl);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String currentIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest().getRemoteAddr();
    }

    private String currentUserAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest().getHeader("User-Agent");
    }
}
