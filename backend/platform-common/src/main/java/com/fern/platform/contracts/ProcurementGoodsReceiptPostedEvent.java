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
        Integer eventVersion,
        Long goodsReceiptId,
        Long purchaseOrderId,
        Long regionId,
        Long outletId,
        LocalDate businessDate,
        Instant postedAt,
        Long postedByUserId,
        List<GoodsReceiptPostedLine> lines
) {
    public static final int CURRENT_VERSION = 1;

    public ProcurementGoodsReceiptPostedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public ProcurementGoodsReceiptPostedEvent(
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
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                goodsReceiptId,
                purchaseOrderId,
                regionId,
                outletId,
                businessDate,
                postedAt,
                postedByUserId,
                lines
        );
    }
}
