package com.fern.iamservice.dto;

import java.util.List;

public record AssignUserScopesRequest(
        Boolean system,
        List<Long> regionIds,
        List<Long> outletIds
) {
}
