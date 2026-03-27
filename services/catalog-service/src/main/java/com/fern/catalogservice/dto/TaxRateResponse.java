package com.fern.catalogservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxRateResponse(
        Long id,
        Long productId,
        BigDecimal taxPercent,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
