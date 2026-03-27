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
        Long paymentId,
        Long supplierId,
        Instant paymentTime,
        BigDecimal amount,
        String currencyCode,
        List<SupplierPaymentAllocation> invoiceAllocations,
        Long recordedByUserId
) {
    public SupplierPaymentRecordedEvent {
        invoiceAllocations = invoiceAllocations == null ? List.of() : List.copyOf(invoiceAllocations);
    }
}
