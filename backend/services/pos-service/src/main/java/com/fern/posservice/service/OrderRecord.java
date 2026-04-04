package com.fern.posservice.service;

import java.math.BigDecimal;
import java.time.Instant;

record OrderRecord(
        Long id,
        String orderNumber,
        Long regionId,
        Long outletId,
        Long posSessionId,
        String currencyCode,
        String orderType,
        String status,
        String paymentStatus,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String promotionCode,
        String note,
        Instant createdAt,
        Instant completedAt,
        Long reservationId,
        Long customerId,
        Long tableId
) {
}
