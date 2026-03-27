package com.fern.iamservice.dto;

import java.time.Instant;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        UserResponse user
) {
}
