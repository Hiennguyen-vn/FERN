package com.fern.iamservice.dto;

import com.fern.iamservice.domain.PermissionOverrideMode;
import java.time.Instant;

public record PermissionOverrideItemResponse(
        String permissionCode,
        PermissionOverrideMode overrideMode,
        String reason,
        Instant expiresAt
) {
}
