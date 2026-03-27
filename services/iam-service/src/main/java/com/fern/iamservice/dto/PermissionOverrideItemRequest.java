package com.fern.iamservice.dto;

import com.fern.iamservice.domain.PermissionOverrideMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PermissionOverrideItemRequest(
        @NotBlank String permissionCode,
        @NotNull PermissionOverrideMode overrideMode,
        String reason,
        Instant expiresAt
) {
}
