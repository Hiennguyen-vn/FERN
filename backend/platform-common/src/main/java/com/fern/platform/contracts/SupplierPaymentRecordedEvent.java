package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SupplierPaymentRecordedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long paymentId,
        Long supplierId,
        Instant paymentTime,
        BigDecimal amount,
        String currencyCode,
        List<SupplierPaymentAllocation> invoiceAllocations,
        Long recordedByUserId
) {
    public static final int CURRENT_VERSION = 1;

    public SupplierPaymentRecordedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        invoiceAllocations = invoiceAllocations == null ? List.of() : List.copyOf(invoiceAllocations);
    }

    public SupplierPaymentRecordedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long paymentId,
            Long supplierId,
            Instant paymentTime,
            BigDecimal amount,
            String currencyCode,
            List<SupplierPaymentAllocation> invoiceAllocations,
            Long recordedByUserId
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                paymentId,
                supplierId,
                paymentTime,
                amount,
                currencyCode,
                invoiceAllocations,
                recordedByUserId
        );
    }
}
