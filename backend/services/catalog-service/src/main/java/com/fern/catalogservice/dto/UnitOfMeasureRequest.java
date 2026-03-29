package com.fern.catalogservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UnitOfMeasureRequest(
        @NotBlank String code,
        @NotBlank String name,
        String symbol
) {
}
