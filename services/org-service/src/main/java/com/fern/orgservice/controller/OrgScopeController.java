package com.fern.orgservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.orgservice.dto.ExpandedScopeResponse;
import com.fern.orgservice.dto.ScopeExpansionRequest;
import com.fern.orgservice.service.OrgAuthorizer;
import com.fern.orgservice.service.ScopeExpansionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/scopes")
public class OrgScopeController {
    private final ScopeExpansionService scopeExpansionService;
    private final OrgAuthorizer orgAuthorizer;

    public OrgScopeController(ScopeExpansionService scopeExpansionService, OrgAuthorizer orgAuthorizer) {
        this.scopeExpansionService = scopeExpansionService;
        this.orgAuthorizer = orgAuthorizer;
    }

    @PostMapping("/expand")
    public ExpandedScopeResponse expand(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody ScopeExpansionRequest request
    ) {
        orgAuthorizer.requirePermission(principal, "org.scope.resolve");
        return scopeExpansionService.expand(request.regionIds(), request.outletIds());
    }
}
