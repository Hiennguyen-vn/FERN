package com.fern.iamservice.dto;

import com.fern.iamservice.domain.RoleStatus;
import java.util.Set;

public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        RoleStatus status,
        Set<String> permissionCodes
) {
}
