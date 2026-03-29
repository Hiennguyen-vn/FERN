package com.fern.procurementservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record SupplierRecord(Long id, String status, Instant approvedAt) {
}

record PurchaseOrderRecord(
        Long id,
        String poNumber,
        Long regionId,
        Long outletId,
        Long supplierId,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String note,
        Instant approvedAt,
        Instant issuedAt
) {
}

record GoodsReceiptRecord(
        Long id,
        String receiptNumber,
        Long purchaseOrderId,
        Long regionId,
        Long outletId,
        Long supplierId,
        Instant receiptTime,
        LocalDate businessDate,
        String status,
        BigDecimal totalAmount,
        String supplierLotNumber,
        String note,
        Instant receivedAt,
        Instant postedAt
) {
}

record SupplierInvoiceRecord(
        Long id,
        Long supplierId,
        Long regionId,
        Long outletId,
        String currencyCode,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String status,
        String note,
        Instant approvedAt
) {
}

record PurchaseOrderTotals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount) {
}
