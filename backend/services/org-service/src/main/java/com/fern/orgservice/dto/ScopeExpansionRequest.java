package com.fern.orgservice.dto;

import java.util.List;

public record ScopeExpansionRequest(
        List<Long> regionIds,
        List<Long> outletIds
) {
}
