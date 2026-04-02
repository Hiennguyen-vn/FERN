package com.fern.catalogservice.controller;

import com.fern.catalogservice.dto.ProductAvailabilityResponse;
import com.fern.catalogservice.dto.ProductAvailabilityUpsertRequest;
import com.fern.catalogservice.dto.ProductPriceResponse;
import com.fern.catalogservice.dto.ProductPriceUpsertRequest;
import com.fern.catalogservice.dto.TaxRateResponse;
import com.fern.catalogservice.dto.TaxRateUpsertRequest;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.CatalogPricingService;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "Catalog — Pricing")
public class CatalogPricingController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final CatalogPricingService catalogPricingService;

    public CatalogPricingController(CatalogAuthorizer catalogAuthorizer, CatalogPricingService catalogPricingService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.catalogPricingService = catalogPricingService;
    }

    @Operation(summary = "Get Catalog — Pricing")
    @GetMapping("/tax-rates")
    public List<TaxRateResponse> listTaxRates(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermissionAnyScope(principal, PermissionCodes.CATALOG_PRICE_READ);
        return catalogPricingService.listTaxRates();
    }

    @Operation(summary = "Get Catalog — Pricing")
    @GetMapping("/tax-rates/{id}")
    public TaxRateResponse getTaxRate(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermissionAnyScope(principal, PermissionCodes.CATALOG_PRICE_READ);
        return catalogPricingService.getTaxRate(id);
    }

    @Operation(summary = "Create or execute Catalog — Pricing")
    @PostMapping("/tax-rates")
    public TaxRateResponse createTaxRate(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody TaxRateUpsertRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRICE_WRITE);
        return catalogPricingService.createTaxRate(principal, request);
    }

    @Operation(summary = "Update Catalog — Pricing")
    @PutMapping("/tax-rates/{id}")
    public TaxRateResponse updateTaxRate(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TaxRateUpsertRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRICE_WRITE);
        return catalogPricingService.updateTaxRate(principal, id, request);
    }

    @Operation(summary = "Get Catalog — Pricing")
    @GetMapping("/product-prices")
    public List<ProductPriceResponse> listProductPrices(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermissionAnyScope(principal, PermissionCodes.CATALOG_PRICE_READ);
        return catalogPricingService.listProductPrices();
    }

    @Operation(summary = "Get Catalog — Pricing")
    @GetMapping("/product-prices/{id}")
    public ProductPriceResponse getProductPrice(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermissionAnyScope(principal, PermissionCodes.CATALOG_PRICE_READ);
        return catalogPricingService.getProductPrice(id);
    }

    @Operation(summary = "Create or execute Catalog — Pricing")
    @PostMapping("/product-prices")
    public ProductPriceResponse createProductPrice(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody ProductPriceUpsertRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRICE_WRITE);
        return catalogPricingService.createProductPrice(principal, request);
    }

    @Operation(summary = "Update Catalog — Pricing")
    @PutMapping("/product-prices/{id}")
    public ProductPriceResponse updateProductPrice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ProductPriceUpsertRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRICE_WRITE);
        return catalogPricingService.updateProductPrice(principal, id, request);
    }

    @Operation(summary = "Get Catalog — Pricing")
    @GetMapping("/product-availability")
    public List<ProductAvailabilityResponse> listAvailability(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long outletId
    ) {
        catalogAuthorizer.requirePermissionAnyScope(principal, PermissionCodes.CATALOG_PRICE_READ);
        return catalogPricingService.listAvailability(productId, outletId);
    }

    @Operation(summary = "Update Catalog — Pricing")
    @PutMapping("/product-availability")
    public ProductAvailabilityResponse upsertAvailability(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody ProductAvailabilityUpsertRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRICE_WRITE);
        return catalogPricingService.upsertAvailability(principal, request);
    }
}
