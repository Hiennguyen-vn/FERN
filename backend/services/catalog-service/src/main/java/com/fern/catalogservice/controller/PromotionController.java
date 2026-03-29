package com.fern.catalogservice.controller;

import com.fern.catalogservice.dto.PromotionResponse;
import com.fern.catalogservice.dto.PromotionUpsertRequest;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.PromotionService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog/promotions")
public class PromotionController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final PromotionService promotionService;

    public PromotionController(CatalogAuthorizer catalogAuthorizer, PromotionService promotionService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.promotionService = promotionService;
    }

    @GetMapping
    public List<PromotionResponse> list(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PROMOTION_READ);
        return promotionService.listPromotions(principal, scopeType, scopeId);
    }

    @PostMapping
    public PromotionResponse create(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody PromotionUpsertRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PROMOTION_WRITE);
        return promotionService.createPromotion(principal, request);
    }

    @PutMapping("/{id}")
    public PromotionResponse update(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PromotionUpsertRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PROMOTION_WRITE);
        return promotionService.updatePromotion(principal, id, request);
    }

    @PostMapping("/{id}/deactivate")
    public PromotionResponse deactivate(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PROMOTION_WRITE);
        return promotionService.deactivatePromotion(principal, id);
    }
}
