package com.fern.catalogservice.controller;

import com.fern.catalogservice.dto.CategoryRequest;
import com.fern.catalogservice.dto.CategoryResponse;
import com.fern.catalogservice.dto.UnitOfMeasureRequest;
import com.fern.catalogservice.dto.UnitOfMeasureResponse;
import com.fern.catalogservice.dto.UomConversionRequest;
import com.fern.catalogservice.dto.UomConversionResponse;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.CatalogReferenceService;
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
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "Catalog — Reference")
public class CatalogReferenceController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final CatalogReferenceService catalogReferenceService;

    public CatalogReferenceController(CatalogAuthorizer catalogAuthorizer, CatalogReferenceService catalogReferenceService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.catalogReferenceService = catalogReferenceService;
    }

    @Operation(summary = "Get Catalog — Reference")
    @GetMapping("/product-categories")
    public List<CategoryResponse> listProductCategories(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRODUCT_READ);
        return catalogReferenceService.listProductCategories();
    }

    @Operation(summary = "Create or execute Catalog — Reference")
    @PostMapping("/product-categories")
    public CategoryResponse createProductCategory(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody CategoryRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRODUCT_WRITE);
        return catalogReferenceService.createProductCategory(request);
    }

    @Operation(summary = "Update Catalog — Reference")
    @PutMapping("/product-categories/{code}")
    public CategoryResponse updateProductCategory(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody CategoryRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_PRODUCT_WRITE);
        return catalogReferenceService.upsertProductCategory(code, request);
    }

    @Operation(summary = "Get Catalog — Reference")
    @GetMapping("/ingredient-categories")
    public List<CategoryResponse> listIngredientCategories(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return catalogReferenceService.listIngredientCategories();
    }

    @Operation(summary = "Create or execute Catalog — Reference")
    @PostMapping("/ingredient-categories")
    public CategoryResponse createIngredientCategory(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody CategoryRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.createIngredientCategory(request);
    }

    @Operation(summary = "Update Catalog — Reference")
    @PutMapping("/ingredient-categories/{code}")
    public CategoryResponse updateIngredientCategory(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody CategoryRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertIngredientCategory(code, request);
    }

    @Operation(summary = "Get Catalog — Reference")
    @GetMapping("/units-of-measure")
    public List<UnitOfMeasureResponse> listUnitsOfMeasure(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return catalogReferenceService.listUnitsOfMeasure();
    }

    @Operation(summary = "Create or execute Catalog — Reference")
    @PostMapping("/units-of-measure")
    public UnitOfMeasureResponse createUnitOfMeasure(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody UnitOfMeasureRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.createUnitOfMeasure(request);
    }

    @Operation(summary = "Update Catalog — Reference")
    @PutMapping("/units-of-measure/{code}")
    public UnitOfMeasureResponse updateUnitOfMeasure(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody UnitOfMeasureRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertUnitOfMeasure(code, request);
    }

    @Operation(summary = "Get Catalog — Reference")
    @GetMapping("/uom-conversions")
    public List<UomConversionResponse> listUomConversions(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return catalogReferenceService.listUomConversions();
    }

    @Operation(summary = "Create or execute Catalog — Reference")
    @PostMapping("/uom-conversions")
    public UomConversionResponse createUomConversion(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody UomConversionRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertUomConversion(request);
    }

    @Operation(summary = "Update Catalog — Reference")
    @PutMapping("/uom-conversions")
    public UomConversionResponse updateUomConversion(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody UomConversionRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertUomConversion(request);
    }
}
