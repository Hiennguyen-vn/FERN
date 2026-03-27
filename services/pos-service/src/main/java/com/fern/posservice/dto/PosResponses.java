package com.fern.posservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PosResponses {
        private PosResponses() {
        }

        public record PosSessionResponse(
                        Long id,
                        String sessionCode,
                        Long regionId,
                        Long outletId,
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
                        BigDecimal discrepancyAmount) {
        }

        public record SaleOrderLineResponse(
                        Integer lineNumber,
                        Long productId,
                        String productCode,
                        String productNameSnapshot,
                        BigDecimal unitPrice,
                        BigDecimal qty,
                        BigDecimal discountAmount,
                        BigDecimal taxAmount,
                        BigDecimal lineTotal,
                        String note) {
        }

        public record SalePaymentResponse(
                        Long id,
                        String paymentMethod,
                        BigDecimal amount,
                        String status,
                        Instant paymentTime,
                        String transactionRef) {
        }

        public record SaleOrderResponse(
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
                        String note,
                        Instant createdAt,
                        Instant completedAt,
                        List<SaleOrderLineResponse> lines,
                        List<SalePaymentResponse> payments) {
        }
}
