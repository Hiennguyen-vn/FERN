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

@RestController
@RequestMapping
public class CatalogReferenceController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final CatalogReferenceService catalogReferenceService;

    public CatalogReferenceController(CatalogAuthorizer catalogAuthorizer, CatalogReferenceService catalogReferenceService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.catalogReferenceService = catalogReferenceService;
    }

    @GetMapping("/product-categories")
    public List<CategoryResponse> listProductCategories(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_READ);
        return catalogReferenceService.listProductCategories();
    }

    @PostMapping("/product-categories")
    public CategoryResponse createProductCategory(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody CategoryRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_WRITE);
        return catalogReferenceService.createProductCategory(request);
    }

    @PutMapping("/product-categories/{code}")
    public CategoryResponse updateProductCategory(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody CategoryRequest request
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_WRITE);
        return catalogReferenceService.upsertProductCategory(code, request);
    }

    @GetMapping("/ingredient-categories")
    public List<CategoryResponse> listIngredientCategories(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return catalogReferenceService.listIngredientCategories();
    }

    @PostMapping("/ingredient-categories")
    public CategoryResponse createIngredientCategory(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody CategoryRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.createIngredientCategory(request);
    }

    @PutMapping("/ingredient-categories/{code}")
    public CategoryResponse updateIngredientCategory(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody CategoryRequest request
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertIngredientCategory(code, request);
    }

    @GetMapping("/units-of-measure")
    public List<UnitOfMeasureResponse> listUnitsOfMeasure(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return catalogReferenceService.listUnitsOfMeasure();
    }

    @PostMapping("/units-of-measure")
    public UnitOfMeasureResponse createUnitOfMeasure(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody UnitOfMeasureRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.createUnitOfMeasure(request);
    }

    @PutMapping("/units-of-measure/{code}")
    public UnitOfMeasureResponse updateUnitOfMeasure(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody UnitOfMeasureRequest request
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertUnitOfMeasure(code, request);
    }

    @GetMapping("/uom-conversions")
    public List<UomConversionResponse> listUomConversions(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return catalogReferenceService.listUomConversions();
    }

    @PostMapping("/uom-conversions")
    public UomConversionResponse createUomConversion(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody UomConversionRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertUomConversion(request);
    }

    @PutMapping("/uom-conversions")
    public UomConversionResponse updateUomConversion(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody UomConversionRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return catalogReferenceService.upsertUomConversion(request);
    }
}
