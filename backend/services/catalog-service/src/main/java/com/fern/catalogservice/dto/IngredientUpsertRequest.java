package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.IngredientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record IngredientUpsertRequest(
        @NotBlank String code,
        @NotBlank @Size(max = 150) String name,
        String categoryCode,
        @NotBlank String baseUomCode,
        BigDecimal minStockLevel,
        BigDecimal maxStockLevel,
        @NotNull IngredientStatus status
) {
}
