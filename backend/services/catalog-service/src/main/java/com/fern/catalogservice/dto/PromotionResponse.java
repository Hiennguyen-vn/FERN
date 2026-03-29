package com.fern.catalogservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PromotionResponse(
        Long id,
        String code,
        String name,
        String description,
        String promotionType,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String scopeType,
        Long scopeId,
        BigDecimal minOrderAmount,
        Integer maxUsageTotal,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        Long createdByUserId,
        Long updatedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
