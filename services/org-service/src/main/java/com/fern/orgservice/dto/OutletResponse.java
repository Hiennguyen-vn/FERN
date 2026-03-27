package com.fern.orgservice.dto;

import com.fern.orgservice.domain.OutletStatus;
import java.time.Instant;
import java.time.LocalDate;

public record OutletResponse(
        Long id,
        Long regionId,
        String code,
        String name,
        OutletStatus status,
        String address,
        String phone,
        String email,
        LocalDate openedAt,
        LocalDate closedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
