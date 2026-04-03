package com.fern.iamservice.service;

import com.fern.iamservice.client.OrgScopeExpansionClient;
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
    static final String DUMMY_PASSWORD_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$Ph4yxbFM2DnrUjVhJurd+g$6v0ldzsZwxbJS8EJlxoxXrigppDyYK2GkA2MGBxfqqA";

    private final UserAccountService userAccountService;
    private final UserViewService userViewService;
    private final FernPasswordHasher passwordHasher;
    private final FernJwtService jwtService;
    private final PolicyVersionService policyVersionService;
    private final ScopeVersionBridgeService scopeVersionBridgeService;
    private final RefreshTokenService refreshTokenService;
    private final LoginProtectionService loginProtectionService;
    private final IamAuditService iamAuditService;
    private final OrgScopeExpansionClient orgScopeExpansionClient;
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
            OrgScopeExpansionClient orgScopeExpansionClient,
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
        this.orgScopeExpansionClient = orgScopeExpansionClient;
        this.clock = clock;
    }

    @Transactional
    public AuthTokenResponse login(AuthLoginRequest request, IamRequestMetadata requestMetadata) {
        String username = request.username() == null ? "" : request.username().trim().toLowerCase(java.util.Locale.ROOT);
        if (loginProtectionService.isTemporarilyLocked(username)) {
            iamAuditService.publishSecurityEvent(
                    "iam.auth.login.failed",
                    null,
                    "DENIED",
                    "temporarily_locked",
                    java.util.Map.of("username", username, "lockedUntil", loginProtectionService.lockedUntil(username)),
                    requestMetadata
            );
            throw new UnauthorizedException("Account is temporarily locked");
        }

        UserAccountEntity user = userAccountService.findOptionalByUsername(username).orElse(null);
        boolean passwordMatches = passwordHasher.matches(
                request.password(),
                user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash()
        );
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordMatches) {
            boolean locked = loginProtectionService.recordFailure(username);
            iamAuditService.publishSecurityEvent(
                    "iam.auth.login.failed",
                    user == null ? null : user.getId(),
                    "DENIED",
                    user == null ? "invalid_credentials" : "invalid_credentials_or_status",
                    java.util.Map.of("username", username, "locked", locked),
                    requestMetadata
            );
            if (locked) {
                iamAuditService.publishSecurityEvent(
                        "iam.auth.locked",
                        user == null ? null : user.getId(),
                        "LOCKED",
                        "too_many_failures",
                        java.util.Map.of("username", username),
                        requestMetadata
                );
            }
            throw new UnauthorizedException("Invalid username or password");
        }
        loginProtectionService.clearFailures(username);
        AuthTokenResponse response = issueTokens(user, null, requestMetadata);
        iamAuditService.publishSecurityEvent(
                "iam.auth.login.succeeded",
                user.getId(),
                "ALLOWED",
                null,
                java.util.Map.of("username", user.getUsername()),
                requestMetadata
        );
        return response;
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request, IamRequestMetadata requestMetadata) {
        Long userId = refreshTokenService.requireUserId(request.refreshToken());
        UserAccountEntity user = userAccountService.findById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User is not active");
        }
        return issueTokens(user, request.refreshToken(), requestMetadata);
    }

    public void logout(FernPrincipal principal, String authorization, LogoutRequest request, IamRequestMetadata requestMetadata) {
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
                java.util.Map.of("username", claims.username()),
                requestMetadata
        );
    }

    private AuthTokenResponse issueTokens(UserAccountEntity user, String existingRefreshToken, IamRequestMetadata requestMetadata) {
        var roleCodes = userViewService.roleCodes(user.getId());
        var permissionCodes = userViewService.permissionCodes(user.getId());
        var scopeRoots = userViewService.scopeRoots(user.getId());
        var accessibleScope = orgScopeExpansionClient.expand(scopeRoots);
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
                accessibleScope,
                policyVersion,
                scopeVersion,
                UUID.randomUUID().toString(),
                now,
                expiresAt
        ), jwtService.accessTokenTtl());

        String refreshToken = existingRefreshToken == null
                ? refreshTokenService.issue(user.getId(), requestMetadata)
                : refreshTokenService.rotate(existingRefreshToken, user.getId(), requestMetadata);

        return new AuthTokenResponse(accessToken, refreshToken, expiresAt, userViewService.toResponse(user));
    }
}
