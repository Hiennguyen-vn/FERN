package com.fern.platform.common;

import java.util.Objects;

public class SingleShardResolver implements ShardResolver {
    private final ShardId defaultShardId;

    public SingleShardResolver(ShardId defaultShardId) {
        this.defaultShardId = Objects.requireNonNull(defaultShardId, "defaultShardId");
    }

    @Override
    public ShardId resolve(RouteKey routeKey) {
        return defaultShardId;
    }
}
