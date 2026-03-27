package com.fern.iamservice.service;

import com.fern.iamservice.domain.UserAccountEntity;
import com.fern.iamservice.domain.UserStatus;
import com.fern.iamservice.dto.AuthLoginRequest;
import com.fern.iamservice.dto.AuthTokenResponse;
import com.fern.iamservice.dto.LogoutRequest;
import com.fern.iamservice.dto.RefreshTokenRequest;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.UnauthorizedException;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernPasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountService userAccountService;
    private final UserViewService userViewService;
    private final FernPasswordHasher passwordHasher;
    private final FernJwtService jwtService;
    private final PolicyVersionService policyVersionService;
    private final ScopeVersionBridgeService scopeVersionBridgeService;
    private final RefreshTokenService refreshTokenService;
    private final LoginProtectionService loginProtectionService;
    private final IamAuditService iamAuditService;
    private final Clock clock;

    public AuthService(
            UserAccountService userAccountService,
            UserViewService userViewService,
            FernPasswordHasher passwordHasher,
            FernJwtService jwtService,
            PolicyVersionService policyVersionService,
            ScopeVersionBridgeService scopeVersionBridgeService,
            RefreshTokenService refreshTokenService,
            LoginProtectionService loginProtectionService,
            IamAuditService iamAuditService,
            Clock clock
    ) {
        this.userAccountService = userAccountService;
        this.userViewService = userViewService;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.policyVersionService = policyVersionService;
        this.scopeVersionBridgeService = scopeVersionBridgeService;
        this.refreshTokenService = refreshTokenService;
        this.loginProtectionService = loginProtectionService;
        this.iamAuditService = iamAuditService;
        this.clock = clock;
    }

    @Transactional
    public AuthTokenResponse login(AuthLoginRequest request) {
        if (loginProtectionService.isTemporarilyLocked(request.username())) {
            iamAuditService.publishSecurityEvent(
                    "iam.auth.login.failed",
                    null,
                    "DENIED",
                    "temporarily_locked",
                    java.util.Map.of("username", request.username(), "lockedUntil", loginProtectionService.lockedUntil(request.username()))
            );
            throw new UnauthorizedException("Account is temporarily locked");
        }

        UserAccountEntity user = userAccountService.findOptionalByUsername(request.username()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordHasher.matches(request.password(), user.getPasswordHash())) {
            boolean locked = loginProtectionService.recordFailure(request.username());
            iamAuditService.publishSecurityEvent(
                    "iam.auth.login.failed",
                    user == null ? null : user.getId(),
                    "DENIED",
                    user == null ? "invalid_credentials" : "invalid_credentials_or_status",
                    java.util.Map.of("username", request.username(), "locked", locked)
            );
            if (locked) {
                iamAuditService.publishSecurityEvent(
                        "iam.auth.locked",
                        user == null ? null : user.getId(),
                        "LOCKED",
                        "too_many_failures",
                        java.util.Map.of("username", request.username())
                );
            }
            throw new UnauthorizedException("Invalid username or password");
        }
        loginProtectionService.clearFailures(request.username());
        AuthTokenResponse response = issueTokens(user, null);
        iamAuditService.publishSecurityEvent(
                "iam.auth.login.succeeded",
                user.getId(),
                "ALLOWED",
                null,
                java.util.Map.of("username", user.getUsername())
        );
        return response;
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        Long userId = refreshTokenService.requireUserId(request.refreshToken());
        UserAccountEntity user = userAccountService.findById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User is not active");
        }
        return issueTokens(user, request.refreshToken());
    }

    public void logout(FernPrincipal principal, String authorization, LogoutRequest request) {
        FernJwtClaims claims = jwtService.decode(authorization.substring("Bearer ".length()));
        refreshTokenService.blacklistJti(claims.jti(), claims.expiresAt());
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revoke(request.refreshToken());
        }
        iamAuditService.publishSecurityEvent(
                "iam.auth.logout",
                principal == null ? claims.userId() : principal.userId(),
                "ALLOWED",
                null,
                java.util.Map.of("username", claims.username())
        );
    }

    private AuthTokenResponse issueTokens(UserAccountEntity user, String existingRefreshToken) {
        var roleCodes = userViewService.roleCodes(user.getId());
        var permissionCodes = userViewService.permissionCodes(user.getId());
        var scopeRoots = userViewService.scopeRoots(user.getId());
        long policyVersion = policyVersionService.currentVersion();
        long scopeVersion = scopeVersionBridgeService.currentVersion();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtService.accessTokenTtl());

        String accessToken = jwtService.encode(new FernJwtClaims(
                user.getId(),
                user.getUsername(),
                roleCodes,
                permissionCodes,
                scopeRoots,
                policyVersion,
                scopeVersion,
                UUID.randomUUID().toString(),
                now,
                expiresAt
        ), jwtService.accessTokenTtl());

        String refreshToken = existingRefreshToken == null
                ? refreshTokenService.issue(user.getId())
                : refreshTokenService.rotate(existingRefreshToken, user.getId());

        return new AuthTokenResponse(accessToken, refreshToken, expiresAt, userViewService.toResponse(user));
    }
}
