package com.fern.orgservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.orgservice.dto.CreateOutletRequest;
import com.fern.orgservice.dto.OutletResponse;
import com.fern.orgservice.dto.UpdateOutletRequest;
import com.fern.orgservice.service.OutletService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/outlets")
@Tag(name = "Outlet")
public class OutletController {
    private final OutletService outletService;

    public OutletController(OutletService outletService) {
        this.outletService = outletService;
    }

    @Operation(summary = "Create or execute Outlet")
    @PostMapping
    public OutletResponse create(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateOutletRequest request
    ) {
        return outletService.create(principal, request);
    }

    @Operation(summary = "Get Outlet")
    @GetMapping
    public PageResponse<OutletResponse> list(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        return outletService.list(principal, regionId, status, search, page, size);
    }

    @Operation(summary = "Get Outlet")
    @GetMapping("/{id}")
    public OutletResponse get(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return outletService.get(principal, id);
    }

    @Operation(summary = "Patch Outlet")
    @PatchMapping("/{id}")
    public OutletResponse update(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateOutletRequest request
    ) {
        return outletService.update(principal, id, request);
    }
}
