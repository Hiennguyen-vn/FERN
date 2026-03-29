package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.IngredientStatus;
import java.math.BigDecimal;

public record IngredientResponse(
        Long id,
        String code,
        String name,
        String categoryCode,
        String baseUomCode,
        BigDecimal minStockLevel,
        BigDecimal maxStockLevel,
        IngredientStatus status
) {
}
