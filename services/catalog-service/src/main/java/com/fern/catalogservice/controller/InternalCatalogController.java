package com.fern.catalogservice.controller;

import com.fern.catalogservice.domain.PriceType;
import com.fern.catalogservice.dto.MenuResponse;
import com.fern.catalogservice.dto.PromotionResponse;
import com.fern.catalogservice.dto.RecipeResolutionResponse;
import com.fern.catalogservice.dto.ResolvedPriceResponse;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.CatalogResolutionService;
import com.fern.catalogservice.service.PromotionService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/catalog")
public class InternalCatalogController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final CatalogResolutionService catalogResolutionService;
    private final PromotionService promotionService;
    private final Clock clock;

    public InternalCatalogController(
            CatalogAuthorizer catalogAuthorizer,
            CatalogResolutionService catalogResolutionService,
            PromotionService promotionService,
            Clock clock
    ) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.catalogResolutionService = catalogResolutionService;
        this.promotionService = promotionService;
        this.clock = clock;
    }

    @GetMapping("/menu")
    public MenuResponse resolveMenu(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long countryId,
            @RequestParam(defaultValue = "RETAIL") PriceType priceType,
            @RequestParam(required = false) LocalDate at
    ) {
        catalogAuthorizer.requireInternalPermission(principal, PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        LocalDate businessDate = at == null ? LocalDate.now(clock) : at;
        return catalogResolutionService.resolveMenu(outletId, businessDate, priceType, regionId, countryId);
    }

    @GetMapping("/price-resolution")
    public ResolvedPriceResponse resolvePrice(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long productId,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long countryId,
            @RequestParam(defaultValue = "RETAIL") PriceType priceType,
            @RequestParam(required = false) LocalDate at
    ) {
        catalogAuthorizer.requireInternalPermission(principal, PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        LocalDate businessDate = at == null ? LocalDate.now(clock) : at;
        return catalogResolutionService.resolvePrice(productId, outletId, businessDate, priceType, regionId, countryId);
    }

    @GetMapping("/recipe-resolution")
    public RecipeResolutionResponse resolveRecipe(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long productId,
            @RequestParam(required = false) LocalDate at
    ) {
        catalogAuthorizer.requireInternalPermission(principal, PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        LocalDate businessDate = at == null ? LocalDate.now(clock) : at;
        return catalogResolutionService.resolveRecipe(productId, businessDate);
    }

    @GetMapping("/promotion-resolution")
    public PromotionResponse resolvePromotion(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam String code,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long regionId,
            @RequestParam BigDecimal orderTotal,
            @RequestParam(required = false) LocalDate at
    ) {
        catalogAuthorizer.requireInternalPermission(principal, PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        LocalDate businessDate = at == null ? LocalDate.now(clock) : at;
        return promotionService.resolveApplicablePromotion(code, outletId, regionId, orderTotal, businessDate)
                .orElseThrow(() -> new ResourceNotFoundException("Applicable promotion not found"));
    }
}
