package com.fern.orgservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertExchangeRateRequest(
        @NotBlank String fromCurrencyCode,
        @NotBlank String toCurrencyCode,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal rate,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
