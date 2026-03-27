package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.RecipeVersionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecipeVersionResponse(
        Long id,
        Long recipeId,
        String versionNo,
        BigDecimal yieldQty,
        String yieldUomCode,
        RecipeVersionStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<RecipeVersionIngredientResponse> ingredients
) {
}
