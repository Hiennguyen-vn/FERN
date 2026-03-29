package com.fern.orgservice.dto;

import com.fern.orgservice.domain.OutletStatus;
import java.time.LocalDate;

public record UpdateOutletRequest(
        Long regionId,
        String name,
        OutletStatus status,
        String address,
        String phone,
        String email,
        LocalDate openedAt,
        LocalDate closedAt
) {
}
