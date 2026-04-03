package com.fern.platform.common;

import java.util.Collection;
import java.util.List;
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

    @Override
    public Collection<OperationalShardAccess> allShards() {
        return List.of(access);
    }
}
