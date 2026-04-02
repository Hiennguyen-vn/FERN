package com.fern.iamservice.controller;

import com.fern.iamservice.dto.CreateRoleRequest;
import com.fern.iamservice.dto.RoleResponse;
import com.fern.iamservice.service.IamAuthorizer;
import com.fern.iamservice.service.RoleService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/roles")
@Tag(name = "Roles")
public class RoleController {
    private final RoleService roleService;
    private final IamAuthorizer iamAuthorizer;

    public RoleController(RoleService roleService, IamAuthorizer iamAuthorizer) {
        this.roleService = roleService;
        this.iamAuthorizer = iamAuthorizer;
    }

    @Operation(summary = "Get Roles")
    @GetMapping
    public List<RoleResponse> list(@AuthenticationPrincipal FernPrincipal principal) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_ROLE_READ);
        return roleService.list();
    }

    @Operation(summary = "Create or execute Roles")
    @PostMapping
    public RoleResponse create(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody CreateRoleRequest request) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_ROLE_WRITE);
        return roleService.create(request);
    }
}
