package com.fern.catalogservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionUpsertRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotBlank String promotionType,
        @DecimalMin("0.00") BigDecimal discountPercent,
        @DecimalMin("0.00") BigDecimal discountAmount,
        @NotBlank String scopeType,
        Long scopeId,
        @DecimalMin("0.00") BigDecimal minOrderAmount,
        Integer maxUsageTotal,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
