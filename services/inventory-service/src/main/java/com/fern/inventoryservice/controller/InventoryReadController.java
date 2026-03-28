package com.fern.inventoryservice.controller;

import com.fern.inventoryservice.dto.InventoryResponses.InventoryTransactionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockBalanceResponse;
import com.fern.inventoryservice.service.InventoryService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class InventoryReadController {
    private final InventoryService inventoryService;

    public InventoryReadController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

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
}
