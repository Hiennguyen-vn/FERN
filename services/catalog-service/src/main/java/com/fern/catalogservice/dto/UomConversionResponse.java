package com.fern.catalogservice.dto;

import java.math.BigDecimal;

public record UomConversionResponse(
        String fromUomCode,
        String toUomCode,
        BigDecimal conversionFactor
) {
}
