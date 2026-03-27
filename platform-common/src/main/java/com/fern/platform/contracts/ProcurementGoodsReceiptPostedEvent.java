package com.fern.platform.contracts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProcurementGoodsReceiptPostedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Long goodsReceiptId,
        Long purchaseOrderId,
        Long regionId,
        Long outletId,
        LocalDate businessDate,
        Instant postedAt,
        Long postedByUserId,
        List<GoodsReceiptPostedLine> lines
) {
    public ProcurementGoodsReceiptPostedEvent {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
