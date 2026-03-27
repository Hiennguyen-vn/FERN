package com.fern.iamservice.dto;

import com.fern.iamservice.domain.UserStatus;

public record UpdateUserRequest(
        String fullName,
        String email,
        String phone,
        UserStatus status
) {
}
