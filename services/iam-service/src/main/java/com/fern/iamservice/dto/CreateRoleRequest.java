package com.fern.iamservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotNull Set<String> permissionCodes
) {
}
