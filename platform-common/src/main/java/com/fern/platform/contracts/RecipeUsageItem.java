package com.fern.platform.contracts;

import java.math.BigDecimal;

public record RecipeUsageItem(
        Long ingredientId,
        String ingredientCode,
        String ingredientName,
        String uomCode,
        BigDecimal qty
) {
}
