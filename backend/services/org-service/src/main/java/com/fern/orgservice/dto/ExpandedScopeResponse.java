package com.fern.orgservice.dto;

import java.util.List;

public record ExpandedScopeResponse(
        List<Long> regionIds,
        List<Long> outletIds,
        long scopeVersion
) {
}
