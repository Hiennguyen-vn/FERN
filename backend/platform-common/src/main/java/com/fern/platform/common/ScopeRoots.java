package com.fern.platform.common;

import java.util.List;

public record ScopeRoots(
        boolean system,
        List<Long> regions,
        List<Long> outlets
) {
    public ScopeRoots(List<Long> regions, List<Long> outlets) {
        this(false, regions, outlets);
    }

    public ScopeRoots {
        regions = regions == null ? List.of() : List.copyOf(regions);
        outlets = outlets == null ? List.of() : List.copyOf(outlets);
    }

    public static ScopeRoots empty() {
        return new ScopeRoots(false, List.of(), List.of());
    }
}
