package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.PriceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPriceUpsertRequest(
        @NotNull Long productId,
        @NotNull PriceScopeType scopeType,
        Long scopeId,
        @NotNull PriceType priceType,
        @NotBlank String currencyCode,
        @NotNull @DecimalMin("0.00") BigDecimal priceValue,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
