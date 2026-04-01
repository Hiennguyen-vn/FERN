package com.fern.orgservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.orgservice.dto.CreateRegionRequest;
import com.fern.orgservice.dto.RegionResponse;
import com.fern.orgservice.dto.UpdateRegionRequest;
import com.fern.orgservice.service.RegionService;
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

@RestController
@RequestMapping("/regions")
public class RegionController {
    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @PostMapping
    public RegionResponse create(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateRegionRequest request
    ) {
        return regionService.create(principal, request);
    }

    @GetMapping
    public PageResponse<RegionResponse> list(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        return regionService.list(principal, search, page, size);
    }

    @GetMapping("/{id}")
    public RegionResponse get(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return regionService.get(principal, id);
    }

    @PatchMapping("/{id}")
    public RegionResponse update(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRegionRequest request
    ) {
        return regionService.update(principal, id, request);
    }
}
