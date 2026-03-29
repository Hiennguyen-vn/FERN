package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.PriceType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPriceResponse(
        Long id,
        Long productId,
        PriceScopeType scopeType,
        Long scopeId,
        PriceType priceType,
        String currencyCode,
        BigDecimal priceValue,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
