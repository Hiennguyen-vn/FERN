package com.fern.iamservice.controller;

import com.fern.iamservice.dto.AssignUserRolesRequest;
import com.fern.iamservice.dto.AssignUserScopesRequest;
import com.fern.iamservice.dto.CreateUserRequest;
import com.fern.iamservice.dto.EffectiveAccessResponse;
import com.fern.iamservice.dto.PutUserPermissionOverridesRequest;
import com.fern.iamservice.dto.UpdateUserRequest;
import com.fern.iamservice.dto.UserResponse;
import com.fern.iamservice.dto.UserPermissionOverridesResponse;
import com.fern.iamservice.domain.UserStatus;
import com.fern.iamservice.service.IamAuthorizer;
import com.fern.iamservice.service.IamRequestMetadata;
import com.fern.iamservice.service.UserService;
import com.fern.iamservice.service.UserPermissionOverrideService;
import com.fern.iamservice.service.UserViewService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/users")
@Tag(name = "Users")
public class UserController {
    private final UserService userService;
    private final IamAuthorizer iamAuthorizer;
    private final UserPermissionOverrideService userPermissionOverrideService;
    private final UserViewService userViewService;

    public UserController(
            UserService userService,
            IamAuthorizer iamAuthorizer,
            UserPermissionOverrideService userPermissionOverrideService,
            UserViewService userViewService
    ) {
        this.userService = userService;
        this.iamAuthorizer = iamAuthorizer;
        this.userPermissionOverrideService = userPermissionOverrideService;
        this.userViewService = userViewService;
    }

    @Operation(summary = "Create or execute Users")
    @PostMapping
    public UserResponse create(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_USER_WRITE);
        return userService.create(principal, request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Get Users")
    @GetMapping
    public PageResponse<UserResponse> list(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_USER_READ);
        return userService.list(search, status, page, size);
    }

    @Operation(summary = "Get Users")
    @GetMapping("/{id}")
    public UserResponse get(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_USER_READ);
        return userService.get(id);
    }

    @Operation(summary = "Patch Users")
    @PatchMapping("/{id}")
    public UserResponse update(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_USER_WRITE);
        return userService.update(principal, id, request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Create or execute Users")
    @PostMapping("/{id}/roles")
    public UserResponse assignRoles(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AssignUserRolesRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_ROLE_ASSIGN);
        return userService.assignRoles(principal, id, request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Create or execute Users")
    @PostMapping("/{id}/scopes")
    public UserResponse assignScopes(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AssignUserScopesRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_SCOPE_ASSIGN);
        return userService.assignScopes(principal, id, request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Get Users")
    @GetMapping("/{id}/permission-overrides")
    public UserPermissionOverridesResponse getPermissionOverrides(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_PERMISSION_OVERRIDE_READ);
        return userPermissionOverrideService.get(id);
    }

    @Operation(summary = "Update Users")
    @PutMapping("/{id}/permission-overrides")
    public UserPermissionOverridesResponse replacePermissionOverrides(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PutUserPermissionOverridesRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            HttpServletRequest httpRequest
    ) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_PERMISSION_OVERRIDE_WRITE);
        return userPermissionOverrideService.replace(principal, id, request, IamRequestMetadata.from(httpRequest, correlationId));
    }

    @Operation(summary = "Get Users")
    @GetMapping("/{id}/effective-access")
    public EffectiveAccessResponse effectiveAccess(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_USER_READ);
        return userViewService.effectiveAccess(id);
    }
}
