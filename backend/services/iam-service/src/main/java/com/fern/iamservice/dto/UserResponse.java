package com.fern.iamservice.dto;

import com.fern.iamservice.domain.UserStatus;
import com.fern.platform.common.ScopeRoots;
import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        UserStatus status,
        Set<String> roleCodes,
        ScopeRoots scopeRoots
) {
}
