package com.fern.catalogservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxRateUpsertRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal taxPercent,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
