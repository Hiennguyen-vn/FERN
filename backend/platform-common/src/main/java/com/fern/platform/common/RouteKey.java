package com.fern.platform.common;

public record RouteKey(Long regionId, Long outletId) {
    public static RouteKey of(Long regionId, Long outletId) {
        return new RouteKey(regionId, outletId);
    }
}
