package com.fern.platform.contracts;

import java.math.BigDecimal;

public record SaleReservationItem(
        Long ingredientId,
        String ingredientCode,
        String ingredientName,
        String uomCode,
        BigDecimal qty
) {
}
