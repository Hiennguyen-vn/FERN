package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SupplierInvoiceApprovedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long supplierInvoiceId,
        Long supplierId,
        Long regionId,
        Long outletId,
        String currencyCode,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal matchedReceiptAmount,
        BigDecimal varianceAmount,
        Instant approvedAt,
        Long approvedByUserId,
        List<SupplierInvoiceApprovedLine> lines
) {
    public static final int CURRENT_VERSION = 1;

    public SupplierInvoiceApprovedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public SupplierInvoiceApprovedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long supplierInvoiceId,
            Long supplierId,
            Long regionId,
            Long outletId,
            String currencyCode,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal matchedReceiptAmount,
            BigDecimal varianceAmount,
            Instant approvedAt,
            Long approvedByUserId,
            List<SupplierInvoiceApprovedLine> lines
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                supplierInvoiceId,
                supplierId,
                regionId,
                outletId,
                currencyCode,
                invoiceNumber,
                invoiceDate,
                dueDate,
                subtotalAmount,
                taxAmount,
                totalAmount,
                matchedReceiptAmount,
                varianceAmount,
                approvedAt,
                approvedByUserId,
                lines
        );
    }
}
