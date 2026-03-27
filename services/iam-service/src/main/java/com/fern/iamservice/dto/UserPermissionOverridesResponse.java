package com.fern.iamservice.dto;

import java.util.List;

public record UserPermissionOverridesResponse(
        Long userId,
        List<PermissionOverrideItemResponse> overrides
) {
}
