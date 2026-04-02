package com.fern.iamservice.controller;

import com.fern.iamservice.dto.AuthLoginRequest;
import com.fern.iamservice.dto.AuthTokenResponse;
import com.fern.iamservice.dto.LogoutRequest;
import com.fern.iamservice.dto.RefreshTokenRequest;
import com.fern.iamservice.service.AuthService;
import com.fern.iamservice.service.IamRequestMetadata;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Create or execute Authentication")
    @PostMapping("/login")
    public AuthTokenResponse login(
            @Valid @RequestBody AuthLoginRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        return authService.login(request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Create or execute Authentication")
    @PostMapping("/refresh")
    public AuthTokenResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        return authService.refresh(request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Create or execute Authentication")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest,
            @RequestBody(required = false) LogoutRequest request
    ) {
        authService.logout(principal, authorization, request, IamRequestMetadata.from(httpRequest, correlationId));
        return ResponseEntity.noContent().build();
    }
}
