package com.fern.catalogservice.dto;

public record ProductAvailabilityResponse(
        Long productId,
        Long outletId,
        boolean available
) {
}
