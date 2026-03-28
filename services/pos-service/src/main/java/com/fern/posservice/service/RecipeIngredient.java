package com.fern.posservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
record RecipeIngredient(
        Long ingredientId,
        String ingredientCode,
        String ingredientName,
        String uomCode,
        BigDecimal qty,
        Integer sortOrder
) {
}
