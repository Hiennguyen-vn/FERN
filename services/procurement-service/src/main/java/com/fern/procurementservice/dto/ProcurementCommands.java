package com.fern.procurementservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProcurementCommands {
    private ProcurementCommands() {
    }

    public record SupplierUpsertRequest(
            @NotNull String supplierCode,
            @NotNull String name,
            String taxCode,
            String email,
            String phone,
            String address,
            Long defaultRegionId,
            String status
    ) {
    }

    public record PurchaseOrderLineInput(
            @NotNull Long ingredientId,
            @NotNull String uomCode,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qtyOrdered,
            BigDecimal expectedUnitPrice,
            BigDecimal taxPercent,
            String note
    ) {
    }

    public record CreatePurchaseOrderRequest(
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull Long supplierId,
            @NotNull LocalDate orderDate,
            LocalDate expectedDeliveryDate,
            String note,
            @NotEmpty List<@Valid PurchaseOrderLineInput> lines
    ) {
    }

    public record UpdatePurchaseOrderRequest(
            LocalDate expectedDeliveryDate,
            String note,
            @NotEmpty List<@Valid PurchaseOrderLineInput> lines
    ) {
    }

    public record GoodsReceiptLineInput(
            Long purchaseOrderLineId,
            @NotNull Long ingredientId,
            @NotNull String uomCode,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qtyReceived,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitCost,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            String note
    ) {
    }

    public record CreateGoodsReceiptRequest(
            @NotNull Long purchaseOrderId,
            @NotNull Instant receiptTime,
            @NotNull LocalDate businessDate,
            String supplierLotNumber,
            String note,
            @NotEmpty List<@Valid GoodsReceiptLineInput> lines
    ) {
    }

    public record SupplierInvoiceLineInput(
            @NotNull String lineType,
            Long goodsReceiptLineId,
            String description,
            BigDecimal qtyInvoiced,
            BigDecimal unitPrice,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            @NotNull @DecimalMin(value = "0.00") BigDecimal lineTotal,
            String note
    ) {
    }

    public record CreateSupplierInvoiceRequest(
            @NotNull Long supplierId,
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull String currencyCode,
            @NotNull String invoiceNumber,
            @NotNull LocalDate invoiceDate,
            LocalDate dueDate,
            String note,
            @NotEmpty List<@Valid SupplierInvoiceLineInput> lines
    ) {
    }

    public record PaymentAllocationInput(
            @NotNull Long supplierInvoiceId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal allocatedAmount,
            String note
    ) {
    }

    public record CreateSupplierPaymentRequest(
            @NotNull Long supplierId,
            @NotNull String currencyCode,
            @NotNull String paymentMethod,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull Instant paymentTime,
            String transactionRef,
            String note,
            @NotEmpty List<@Valid PaymentAllocationInput> invoiceAllocations
    ) {
    }
}
