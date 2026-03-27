package com.fern.catalogservice.dto;

public record CategoryResponse(
        String code,
        String name,
        String description,
        boolean active
) {
}
