package com.fern.catalogservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecipeUpsertRequest(
        @NotNull Long productId,
        @NotBlank String recipeCode,
        String description
) {
}
