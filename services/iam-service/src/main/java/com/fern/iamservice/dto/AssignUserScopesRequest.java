package com.fern.iamservice.dto;

import java.util.List;

public record AssignUserScopesRequest(
        List<Long> regionIds,
        List<Long> outletIds
) {
}
