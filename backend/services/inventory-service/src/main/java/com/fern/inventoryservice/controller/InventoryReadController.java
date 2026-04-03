package com.fern.inventoryservice.controller;

import com.fern.inventoryservice.dto.InventoryResponses.InventoryTransactionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockBalanceResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountSessionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountSessionSummaryResponse;
import com.fern.inventoryservice.service.InventoryService;
import com.fern.inventoryservice.service.StockCountService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "Inventory")
public class InventoryReadController {
    private final InventoryService inventoryService;
    private final StockCountService stockCountService;

    public InventoryReadController(InventoryService inventoryService, StockCountService stockCountService) {
        this.inventoryService = inventoryService;
        this.stockCountService = stockCountService;
    }

    @Operation(summary = "Get Inventory")
    @GetMapping("/stock-balances")
    public PageResponse<StockBalanceResponse> listStockBalances(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return inventoryService.listStockBalances(principal, outletId, ingredientId, page, size);
    }

    @Operation(summary = "Get Inventory")
    @GetMapping("/inventory-transactions")
    public PageResponse<InventoryTransactionResponse> listInventoryTransactions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return inventoryService.listInventoryTransactions(principal, outletId, ingredientId, txnType, from, to, sourceType, sourceId, page, size);
    }

    @Operation(summary = "List stock count sessions for an outlet")
    @GetMapping("/stock-count-sessions")
    public PageResponse<StockCountSessionSummaryResponse> listStockCountSessions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return stockCountService.listStockCountSessions(principal, outletId, status, page, size);
    }

    @Operation(summary = "Get stock count session with lines")
    @GetMapping("/stock-count-sessions/{id}")
    public StockCountSessionResponse getStockCountSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return stockCountService.readStockCountSession(principal, id);
    }
}
