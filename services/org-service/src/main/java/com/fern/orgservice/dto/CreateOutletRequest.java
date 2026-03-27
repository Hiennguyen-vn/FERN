package com.fern.orgservice.dto;

import com.fern.orgservice.domain.OutletStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateOutletRequest(
        @NotNull Long regionId,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull OutletStatus status,
        String address,
        String phone,
        String email,
        LocalDate openedAt,
        LocalDate closedAt
) {
}
