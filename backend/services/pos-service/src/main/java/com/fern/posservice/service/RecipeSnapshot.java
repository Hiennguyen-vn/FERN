package com.fern.posservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record RecipeSnapshot(
        Long productId,
        Long recipeId,
        Long recipeVersionId,
        String recipeCode,
        String versionNo,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<RecipeIngredient> ingredients
) {
}
