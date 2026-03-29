package com.fern.orgservice.dto;

public record UpdateRegionRequest(
        Long parentRegionId,
        String currencyCode,
        String name,
        String taxCode,
        String timezoneName
) {
}
