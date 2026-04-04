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
                        BigDecimal discrepancyAmount) {
        }

        public record OutletTodayStatResponse(
                        Long outletId,
                        Long sessionId,
                        String sessionStatus,
                        String currencyCode,
                        Long totalOrders,
                        Long completed,
                        Long open,
                        Long cancelled,
                        BigDecimal totalRevenue,
                        BigDecimal cashCollected,
                        BigDecimal nonCashCollected) {
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

        public record CustomerSummaryResponse(
                        Long id,
                        String customerCode,
                        String fullName,
                        String phone,
                        String loyaltyTier) {
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
                        String promotionCode,
                        String note,
                        Instant createdAt,
                        Instant completedAt,
                        Long tableId,
                        String tableName,
                        CustomerSummaryResponse customer,
                        List<SaleOrderLineResponse> lines,
                        List<SalePaymentResponse> payments) {
        }

        public record CustomerResponse(
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
                        Instant createdAt) {
        }

        public record LoyaltyTransactionResponse(
                        Long id,
                        Long customerId,
                        Long saleOrderId,
                        Long outletId,
                        String txnType,
                        int points,
                        long balanceAfter,
                        String description,
                        Instant createdAt) {
        }

        public record DiningTableResponse(
                        Long id,
                        Long outletId,
                        String tableName,
                        String tableCode,
                        Integer capacity,
                        String zone,
                        String status,
                        Long currentOrderId,
                        String note,
                        Instant createdAt,
                        Instant updatedAt) {
        }
}
