package com.fern.platform.common;

import java.util.Collection;

public interface OperationalShardRegistry {
    OperationalShardAccess get(ShardId shardId);

    /** Returns all registered shard accesses. Used for cross-shard scans (e.g. scheduled recovery jobs). */
    Collection<OperationalShardAccess> allShards();

    default OperationalShardAccess get(RouteKey routeKey, ShardResolver shardResolver) {
        return get(shardResolver.resolve(routeKey));
    }
}
