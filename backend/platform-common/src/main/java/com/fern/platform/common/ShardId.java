package com.fern.platform.common;

import java.util.Objects;

public record ShardId(String value) {
    public ShardId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Shard id cannot be blank");
        }
    }
}
