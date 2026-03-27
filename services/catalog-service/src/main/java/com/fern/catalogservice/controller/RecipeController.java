package com.fern.catalogservice.controller;

import com.fern.catalogservice.dto.RecipeResponse;
import com.fern.catalogservice.dto.RecipeUpsertRequest;
import com.fern.catalogservice.dto.RecipeVersionResponse;
import com.fern.catalogservice.dto.RecipeVersionUpsertRequest;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.RecipeService;
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
@RequestMapping
public class RecipeController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final RecipeService recipeService;

    public RecipeController(CatalogAuthorizer catalogAuthorizer, RecipeService recipeService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.recipeService = recipeService;
    }

    @GetMapping("/recipes")
    public List<RecipeResponse> listRecipes(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_READ);
        return recipeService.listRecipes();
    }

    @GetMapping("/recipes/{id}")
    public RecipeResponse getRecipe(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_READ);
        return recipeService.getRecipe(id);
    }

    @PostMapping("/recipes")
    public RecipeResponse createRecipe(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody RecipeUpsertRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_WRITE);
        return recipeService.createRecipe(principal, request);
    }

    @PutMapping("/recipes/{id}")
    public RecipeResponse updateRecipe(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RecipeUpsertRequest request
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_WRITE);
        return recipeService.updateRecipe(principal, id, request);
    }

    @GetMapping("/recipe-versions")
    public List<RecipeVersionResponse> listRecipeVersions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long recipeId
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_READ);
        return recipeService.listRecipeVersions(recipeId);
    }

    @GetMapping("/recipe-versions/{id}")
    public RecipeVersionResponse getRecipeVersion(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_READ);
        return recipeService.getRecipeVersion(id);
    }

    @PostMapping("/recipe-versions")
    public RecipeVersionResponse createRecipeVersion(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody RecipeVersionUpsertRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_WRITE);
        return recipeService.createRecipeVersion(principal, request);
    }

    @PutMapping("/recipe-versions/{id}")
    public RecipeVersionResponse updateRecipeVersion(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RecipeVersionUpsertRequest request
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_WRITE);
        return recipeService.updateRecipeVersion(principal, id, request);
    }

    @PostMapping("/recipe-versions/{id}/activate")
    public RecipeVersionResponse activateRecipeVersion(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_WRITE);
        return recipeService.activateRecipeVersion(principal, id);
    }

    @PostMapping("/recipe-versions/{id}/archive")
    public RecipeVersionResponse archiveRecipeVersion(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_RECIPE_WRITE);
        return recipeService.archiveRecipeVersion(principal, id);
    }
}
