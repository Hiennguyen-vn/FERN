package com.fern.auditservice.repository;

import java.util.List;

public record AuditAccessScope(
        boolean system,
        List<Long> regions,
        List<Long> outlets
) {
    public AuditAccessScope {
        regions = regions == null ? List.of() : List.copyOf(regions);
        outlets = outlets == null ? List.of() : List.copyOf(outlets);
    }

    public static AuditAccessScope unrestricted() {
        return new AuditAccessScope(true, List.of(), List.of());
    }
}
