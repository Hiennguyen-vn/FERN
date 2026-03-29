package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductUpsertRequest(
        @NotBlank String code,
        @NotBlank @Size(max = 150) String name,
        String categoryCode,
        @NotNull ProductStatus status,
        String imageUrl,
        String description
) {
}
