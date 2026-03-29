package com.fern.catalogservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UomConversionRequest(
        @NotBlank String fromUomCode,
        @NotBlank String toUomCode,
        @NotNull @DecimalMin("0.00000001") BigDecimal conversionFactor
) {
}
