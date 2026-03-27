package com.fern.catalogservice.dto;

public record RecipeResponse(
        Long id,
        Long productId,
        String recipeCode,
        String description
) {
}
