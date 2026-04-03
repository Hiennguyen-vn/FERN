package com.fern.platform.common;

import java.util.Objects;

public class SingleOperationalShardRegistry implements OperationalShardRegistry {
    private final OperationalShardAccess access;

    public SingleOperationalShardRegistry(OperationalShardAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    @Override
    public OperationalShardAccess get(ShardId shardId) {
        if (!access.shardId().equals(shardId)) {
            throw new IllegalArgumentException("Unknown shard id: " + shardId.value());
        }
        return access;
    }
}
