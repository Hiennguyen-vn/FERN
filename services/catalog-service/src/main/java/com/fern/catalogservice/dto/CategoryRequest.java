package com.fern.catalogservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        boolean active
) {
}
