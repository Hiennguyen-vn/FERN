package com.fern.catalogservice.dto;

import java.math.BigDecimal;

public record RecipeVersionIngredientResponse(
        Long ingredientId,
        String ingredientCode,
        String ingredientName,
        String uomCode,
        BigDecimal qty,
        int sortOrder
) {
}
