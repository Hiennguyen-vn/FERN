package com.fern.posservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record CustomerRecord(
        Long id,
        String customerCode,
        String fullName,
        String phone,
        String email,
        LocalDate dob,
        String gender,
        String loyaltyTier,
        long loyaltyPoints,
        BigDecimal totalSpend,
        int visitCount,
        String status,
        String note,
        Long createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
