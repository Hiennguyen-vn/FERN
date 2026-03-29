package com.fern.orgservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRegionRequest(
        @NotBlank String code,
        Long parentRegionId,
        @NotBlank String currencyCode,
        @NotBlank String name,
        String taxCode,
        @NotBlank String timezoneName
) {
}
