package com.fern.posservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

enum PosSessionStatus {
    OPEN,
    CLOSED,
    RECONCILED,
    CANCELLED
}

enum SaleOrderStatus {
    OPEN,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    VOIDED
}

enum SaleOrderPaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    REFUNDED
}

enum SalePaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED
}

enum PosOutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}

final class PosEventTypes {
    static final String SALE_COMPLETED = "pos.sale.completed";

    private PosEventTypes() {
    }
}

final class PosServiceNames {
    static final String POS_SERVICE = "pos-service";
    static final String CATALOG_SERVICE = "catalog-service";
    static final String INVENTORY_SERVICE = "inventory-service";

    private PosServiceNames() {
    }
}

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
        String note,
        Instant createdAt,
        Instant completedAt
) {
}

record PricedLine(
        Integer lineNumber,
        Long productId,
        String productCode,
        String productNameSnapshot,
        BigDecimal unitPrice,
        BigDecimal qty,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        String note
) {
}

record PricingSnapshot(
        List<PricedLine> lines,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record MenuResponse(
        Long outletId,
        LocalDate businessDate,
        List<MenuItem> items
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record MenuItem(
        Long productId,
        String productCode,
        String productName,
        String categoryCode,
        String currencyCode,
        BigDecimal priceValue,
        BigDecimal taxPercent
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record RecipeSnapshot(
        Long productId,
        Long recipeId,
        Long recipeVersionId,
        String recipeCode,
        String versionNo,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<RecipeIngredient> ingredients
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record RecipeIngredient(
        Long ingredientId,
        String ingredientCode,
        String ingredientName,
        String uomCode,
        BigDecimal qty,
        Integer sortOrder
) {
}
