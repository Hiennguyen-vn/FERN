package com.fern.catalogservice.dto;

import java.time.LocalDate;
import java.util.List;

public record RecipeResolutionResponse(
        Long productId,
        Long recipeId,
        Long recipeVersionId,
        String recipeCode,
        String versionNo,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<RecipeVersionIngredientResponse> ingredients
) {
}
