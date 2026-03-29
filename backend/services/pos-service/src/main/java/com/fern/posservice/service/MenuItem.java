package com.fern.posservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
record MenuItem(
        Long productId,
        String productCode,
        String productName,
        String categoryCode,
        String currencyCode,
        BigDecimal priceValue,
        BigDecimal taxPercent
) {
}
