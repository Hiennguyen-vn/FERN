package com.fern.platform.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FernJwksCache {
    private final FernJwksProvider provider;
    private final Clock clock;
    private final Duration refreshInterval;
    private volatile Snapshot snapshot;

    public FernJwksCache(FernJwksProvider provider, Clock clock, Duration refreshInterval) {
        this.provider = provider;
        this.clock = clock;
        this.refreshInterval = refreshInterval;
    }

    public RSAPublicKey resolve(String kid) {
        if (kid == null || kid.isBlank()) {
            return null;
        }
        Snapshot current = current();
        RSAPublicKey key = current.keys().get(kid);
        if (key != null) {
            return key;
        }
        Snapshot refreshed = refresh();
        return refreshed.keys().get(kid);
    }

    public Snapshot current() {
        Snapshot current = snapshot;
        if (current == null || current.expiresAt().isBefore(clock.instant())) {
            return refresh();
        }
        return current;
    }

    public synchronized Snapshot refresh() {
        Map<String, RSAPublicKey> keys = new ConcurrentHashMap<>();
        provider.currentJwkSet().getKeys().forEach(key -> {
            if (key.getKeyID() != null && key.toRSAKey() != null) {
                try {
                    keys.put(key.getKeyID(), key.toRSAKey().toRSAPublicKey());
                } catch (Exception ignored) {
                    // Ignore malformed keys and keep the cache usable for the rest of the set.
                }
            }
        });
        Snapshot refreshed = new Snapshot(Map.copyOf(keys), clock.instant().plus(refreshInterval));
        snapshot = refreshed;
        return refreshed;
    }

    public record Snapshot(Map<String, RSAPublicKey> keys, Instant expiresAt) {
    }
}
