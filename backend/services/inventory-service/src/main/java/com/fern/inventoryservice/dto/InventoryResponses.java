package com.fern.inventoryservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InventoryResponses {
    private InventoryResponses() {
    }

    public record StockBalanceResponse(
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qtyOnHand,
            BigDecimal qtyReserved,
            BigDecimal qtyAvailable,
            BigDecimal unitCost,
            LocalDate lastCountDate
    ) {
    }

    public record InventoryTransactionResponse(
            Long id,
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qtyChange,
            LocalDate businessDate,
            Instant txnTime,
            String txnType,
            BigDecimal unitCost,
            String sourceReferenceType,
            String sourceReferenceId,
            Long createdByUserId
    ) {
    }

    public record StockAdjustmentResponse(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            Long ingredientId,
            String adjustmentDirection,
            BigDecimal qty,
            LocalDate businessDate,
            String reason,
            String note,
            Long inventoryTransactionId,
            Instant postedAt
    ) {
    }

    public record WasteRecordResponse(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qty,
            LocalDate businessDate,
            String reason,
            String note,
            Long inventoryTransactionId,
            Instant postedAt
    ) {
    }

    public record StockCountLineResponse(
            Long ingredientId,
            BigDecimal systemQty,
            BigDecimal actualQty,
            BigDecimal varianceQty,
            String note
    ) {
    }

    public record StockCountSessionResponse(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            LocalDate countDate,
            String note,
            Instant startedAt,
            Instant postedAt,
            List<StockCountLineResponse> lines
    ) {
    }
}
