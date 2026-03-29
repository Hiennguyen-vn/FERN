package com.fern.orgservice.dto;

import java.time.Instant;

public record RegionResponse(
        Long id,
        String code,
        Long parentRegionId,
        String currencyCode,
        String name,
        String taxCode,
        String timezoneName,
        Instant createdAt,
        Instant updatedAt
) {
}
