package com.fern.catalogservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RecipeVersionIngredientRequest(
        @NotNull Long ingredientId,
        @NotBlank String uomCode,
        @NotNull @DecimalMin("0.0001") BigDecimal qty,
        @Positive int sortOrder
) {
}
