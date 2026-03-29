package com.fern.iamservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.iamservice.dto.AuthLoginRequest;
import com.fern.platform.common.UnauthorizedException;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernPasswordHasher;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthServiceTest {
    @Mock
    private UserAccountService userAccountService;

    @Mock
    private UserViewService userViewService;

    @Mock
    private FernPasswordHasher passwordHasher;

    @Mock
    private FernJwtService jwtService;

    @Mock
    private PolicyVersionService policyVersionService;

    @Mock
    private ScopeVersionBridgeService scopeVersionBridgeService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginProtectionService loginProtectionService;

    @Mock
    private IamAuditService iamAuditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(
                userAccountService,
                userViewService,
                passwordHasher,
                jwtService,
                policyVersionService,
                scopeVersionBridgeService,
                refreshTokenService,
                loginProtectionService,
                iamAuditService,
                Clock.systemUTC()
        );
    }

    @Test
    void shouldStillCheckPasswordHashWhenUserDoesNotExist() {
        when(loginProtectionService.isTemporarilyLocked("ghost-user")).thenReturn(false);
        when(userAccountService.findOptionalByUsername("ghost-user")).thenReturn(Optional.empty());
        when(passwordHasher.matches("WrongPassword!", AuthService.DUMMY_PASSWORD_HASH)).thenReturn(false);
        when(loginProtectionService.recordFailure("ghost-user")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new AuthLoginRequest("ghost-user", "WrongPassword!"), IamRequestMetadata.empty()))
                .isInstanceOf(UnauthorizedException.class);

        verify(passwordHasher).matches("WrongPassword!", AuthService.DUMMY_PASSWORD_HASH);
        verify(iamAuditService).publishSecurityEvent(any(), any(), any(), any(), any(), any());
    }
}
