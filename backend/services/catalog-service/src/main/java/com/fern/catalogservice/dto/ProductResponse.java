package com.fern.catalogservice.dto;

import com.fern.catalogservice.domain.ProductStatus;

public record ProductResponse(
        Long id,
        String code,
        String name,
        String categoryCode,
        ProductStatus status,
        String imageUrl,
        String description
) {
}
