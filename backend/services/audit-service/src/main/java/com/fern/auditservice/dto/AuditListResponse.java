package com.fern.auditservice.dto;

import java.util.List;

public record AuditListResponse<T>(List<T> items) {
    public AuditListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
