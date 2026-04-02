package com.fern.inventoryservice.controller;

import com.fern.inventoryservice.dto.InventoryCommands.CreateStockAdjustmentRequest;
import com.fern.inventoryservice.dto.InventoryCommands.CreateStockCountSessionRequest;
import com.fern.inventoryservice.dto.InventoryCommands.CreateWasteRecordRequest;
import com.fern.inventoryservice.dto.InventoryCommands.UpdateStockCountLinesRequest;
import com.fern.inventoryservice.dto.InventoryResponses.StockAdjustmentResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountSessionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.WasteRecordResponse;
import com.fern.inventoryservice.service.StockAdjustmentService;
import com.fern.inventoryservice.service.StockCountService;
import com.fern.inventoryservice.service.WasteRecordService;
import com.fern.platform.common.FernPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "Inventory")
public class InventoryCommandController {
    private final StockAdjustmentService stockAdjustmentService;
    private final WasteRecordService wasteRecordService;
    private final StockCountService stockCountService;

    public InventoryCommandController(
            StockAdjustmentService stockAdjustmentService,
            WasteRecordService wasteRecordService,
            StockCountService stockCountService
    ) {
        this.stockAdjustmentService = stockAdjustmentService;
        this.wasteRecordService = wasteRecordService;
        this.stockCountService = stockCountService;
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-adjustments")
    public StockAdjustmentResponse createStockAdjustment(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateStockAdjustmentRequest request
    ) {
        return stockAdjustmentService.createStockAdjustment(principal, request);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-adjustments/{id}/post")
    public StockAdjustmentResponse postStockAdjustment(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return stockAdjustmentService.postStockAdjustment(principal, id, idempotencyKey);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-adjustments/{id}/cancel")
    public StockAdjustmentResponse cancelStockAdjustment(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return stockAdjustmentService.cancelStockAdjustment(principal, id);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/waste-records")
    public WasteRecordResponse createWasteRecord(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateWasteRecordRequest request
    ) {
        return wasteRecordService.createWasteRecord(principal, request);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/waste-records/{id}/post")
    public WasteRecordResponse postWasteRecord(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return wasteRecordService.postWasteRecord(principal, id, idempotencyKey);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/waste-records/{id}/cancel")
    public WasteRecordResponse cancelWasteRecord(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return wasteRecordService.cancelWasteRecord(principal, id);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-count-sessions")
    public StockCountSessionResponse createStockCountSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateStockCountSessionRequest request
    ) {
        return stockCountService.createStockCountSession(principal, request);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-count-sessions/{id}/start")
    public StockCountSessionResponse startStockCountSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return stockCountService.startStockCountSession(principal, id);
    }

    @Operation(summary = "Update Inventory")
    @PutMapping("/stock-count-sessions/{id}/lines")
    public StockCountSessionResponse updateStockCountLines(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockCountLinesRequest request
    ) {
        return stockCountService.updateStockCountLines(principal, id, request);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-count-sessions/{id}/post")
    public StockCountSessionResponse postStockCountSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return stockCountService.postStockCountSession(principal, id, idempotencyKey);
    }

    @Operation(summary = "Create or execute Inventory")
    @PostMapping("/stock-count-sessions/{id}/cancel")
    public StockCountSessionResponse cancelStockCountSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return stockCountService.cancelStockCountSession(principal, id);
    }
}
