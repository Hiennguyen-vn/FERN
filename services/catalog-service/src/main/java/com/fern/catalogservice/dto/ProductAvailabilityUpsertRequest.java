package com.fern.catalogservice.dto;

import jakarta.validation.constraints.NotNull;

public record ProductAvailabilityUpsertRequest(
        @NotNull Long productId,
        @NotNull Long outletId,
        boolean available
) {
}
