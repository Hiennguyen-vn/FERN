package com.fern.inventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InventoryCommands {
    private InventoryCommands() {
    }

    public record CreateStockAdjustmentRequest(
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull Long ingredientId,
            @NotNull String adjustmentDirection,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            @NotNull LocalDate businessDate,
            @NotNull String reason,
            String note
    ) {
    }

    public record CreateWasteRecordRequest(
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull Long ingredientId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            @NotNull LocalDate businessDate,
            @NotNull String reason,
            String note
    ) {
    }

    public record CreateStockCountSessionRequest(
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull LocalDate countDate,
            String note,
            @NotEmpty List<Long> ingredientIds
    ) {
    }

    public record StockCountLineInput(
            @NotNull Long ingredientId,
            @NotNull @DecimalMin(value = "0.0000") BigDecimal actualQty,
            String note
    ) {
    }

    public record UpdateStockCountLinesRequest(
            @NotEmpty List<@Valid StockCountLineInput> lines
    ) {
    }
}
