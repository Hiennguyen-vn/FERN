package com.fern.iamservice.controller;

import com.fern.iamservice.dto.PermissionResponse;
import com.fern.iamservice.service.IamAuthorizer;
import com.fern.iamservice.service.PermissionService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
public class PermissionController {
    private final PermissionService permissionService;
    private final IamAuthorizer iamAuthorizer;

    public PermissionController(PermissionService permissionService, IamAuthorizer iamAuthorizer) {
        this.permissionService = permissionService;
        this.iamAuthorizer = iamAuthorizer;
    }

    @GetMapping
    public List<PermissionResponse> list(@AuthenticationPrincipal FernPrincipal principal) {
        iamAuthorizer.requirePermission(principal, PermissionCodes.IAM_PERMISSION_READ);
        return permissionService.list();
    }
}
