package com.fern.posservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record SessionRecord(
        Long id,
        String sessionCode,
        Long regionId,
        Long outletId,
        String terminalId,
        String currencyCode,
        Long cashierUserId,
        Long managerUserId,
        LocalDate businessDate,
        String status,
        String note,
        Instant openedAt,
        Instant closedAt,
        Instant reconciledAt,
        BigDecimal expectedCashAmount,
        BigDecimal countedCashAmount,
        BigDecimal discrepancyAmount
) {
}
