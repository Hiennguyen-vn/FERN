package com.fern.catalogservice.controller;

import com.fern.catalogservice.dto.IngredientResponse;
import com.fern.catalogservice.dto.IngredientUpsertRequest;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.IngredientService;
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
@RequestMapping("/ingredients")
@Tag(name = "Catalog — Ingredients")
public class IngredientController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final IngredientService ingredientService;

    public IngredientController(CatalogAuthorizer catalogAuthorizer, IngredientService ingredientService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.ingredientService = ingredientService;
    }

    @Operation(summary = "Get Catalog — Ingredients")
    @GetMapping
    public List<IngredientResponse> list(@AuthenticationPrincipal FernPrincipal principal, @org.springframework.web.bind.annotation.RequestParam(required = false) Integer limit) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return ingredientService.list(com.fern.platform.common.ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Get Catalog — Ingredients")
    @GetMapping("/{id}")
    public IngredientResponse get(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_READ);
        return ingredientService.get(id);
    }

    @Operation(summary = "Create or execute Catalog — Ingredients")
    @PostMapping
    public IngredientResponse create(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody IngredientUpsertRequest request) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return ingredientService.create(principal, request);
    }

    @Operation(summary = "Update Catalog — Ingredients")
    @PutMapping("/{id}")
    public IngredientResponse update(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody IngredientUpsertRequest request
    ) {
        catalogAuthorizer.requireSystemPermission(principal, PermissionCodes.CATALOG_INGREDIENT_WRITE);
        return ingredientService.update(principal, id, request);
    }
}
