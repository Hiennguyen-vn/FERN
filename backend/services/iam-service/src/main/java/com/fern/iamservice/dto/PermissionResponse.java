package com.fern.iamservice.dto;

public record PermissionResponse(
        String code,
        String name,
        String description
) {
}
