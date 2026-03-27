package com.fern.iamservice.dto;

import com.fern.iamservice.domain.UserStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String fullName,
        String email,
        String phone,
        UserStatus status
) {
}
