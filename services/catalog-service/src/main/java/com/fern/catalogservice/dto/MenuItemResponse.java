package com.fern.catalogservice.dto;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long productId,
        String productCode,
        String productName,
        String categoryCode,
        String currencyCode,
        BigDecimal priceValue,
        BigDecimal taxPercent
) {
}
