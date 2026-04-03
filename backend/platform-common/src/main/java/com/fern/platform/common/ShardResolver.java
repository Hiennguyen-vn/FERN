package com.fern.platform.common;

public interface ShardResolver {
    ShardId resolve(RouteKey routeKey);
}
