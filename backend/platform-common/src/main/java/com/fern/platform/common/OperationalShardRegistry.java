package com.fern.platform.common;

public interface OperationalShardRegistry {
    OperationalShardAccess get(ShardId shardId);

    default OperationalShardAccess get(RouteKey routeKey, ShardResolver shardResolver) {
        return get(shardResolver.resolve(routeKey));
    }
}
