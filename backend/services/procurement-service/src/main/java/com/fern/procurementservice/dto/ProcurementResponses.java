package com.fern.procurementservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProcurementResponses {
    private ProcurementResponses() {
    }

    public record SupplierResponse(
            Long id,
            String supplierCode,
            String name,
            String taxCode,
            String email,
            String phone,
            String address,
            Long defaultRegionId,
            String status,
            Instant approvedAt
    ) {
    }

    public record PurchaseOrderLineResponse(
            Long id,
            Integer lineNumber,
            Long ingredientId,
            String uomCode,
            BigDecimal qtyOrdered,
            BigDecimal qtyReceived,
            BigDecimal expectedUnitPrice,
            BigDecimal taxPercent,
            String status,
            String note
    ) {
    }

    public record PurchaseOrderResponse(
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
            Instant issuedAt,
            List<PurchaseOrderLineResponse> lines
    ) {
    }

    public record GoodsReceiptLineResponse(
            Long id,
            Long purchaseOrderLineId,
            Long ingredientId,
            String uomCode,
            BigDecimal qtyReceived,
            BigDecimal unitCost,
            BigDecimal lineTotal,
            String note
    ) {
    }

    public record GoodsReceiptResponse(
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
            Instant postedAt,
            List<GoodsReceiptLineResponse> lines
    ) {
    }

    public record SupplierInvoiceLineResponse(
            Long id,
            Integer lineNumber,
            String lineType,
            Long goodsReceiptLineId,
            String description,
            BigDecimal qtyInvoiced,
            BigDecimal unitPrice,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String note
    ) {
    }

    public record SupplierInvoiceResponse(
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
            Instant approvedAt,
            List<SupplierInvoiceLineResponse> lines
    ) {
    }

    public record SupplierPaymentAllocationResponse(
            Long supplierInvoiceId,
            BigDecimal allocatedAmount,
            String note
    ) {
    }

    public record SupplierPaymentResponse(
            Long id,
            String paymentNumber,
            Long supplierId,
            String currencyCode,
            String paymentMethod,
            BigDecimal amount,
            Instant paymentTime,
            String transactionRef,
            String note,
            List<SupplierPaymentAllocationResponse> invoiceAllocations
    ) {
    }

    public record OutletCloseCheckResponse(
            Long outletId,
            long blockingPurchaseOrders,
            long blockingGoodsReceipts,
            long blockingSupplierInvoices,
            boolean hasBlockingDocuments
    ) {
    }
}
