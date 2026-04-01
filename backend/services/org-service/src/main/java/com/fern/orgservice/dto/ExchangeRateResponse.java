package com.fern.orgservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResponse(
        String fromCurrencyCode,
        String toCurrencyCode,
        BigDecimal rate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
