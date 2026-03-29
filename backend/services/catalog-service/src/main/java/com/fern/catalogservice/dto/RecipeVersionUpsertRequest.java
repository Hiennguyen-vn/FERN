package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.RecipeVersionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecipeVersionUpsertRequest(
        @NotNull Long recipeId,
        @NotBlank String versionNo,
        @NotNull @DecimalMin("0.0001") BigDecimal yieldQty,
        @NotBlank String yieldUomCode,
        @NotNull RecipeVersionStatus status,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Valid @NotEmpty List<RecipeVersionIngredientRequest> ingredients
) {
}
