package com.fern.reportservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.platform.observability.CorrelationId;
import com.fern.reportservice.dto.ReportInventoryResponses.InventoryMovementFactResponse;
import com.fern.reportservice.dto.ReportInventoryResponses.InventoryStockBalanceSnapshotResponse;
import com.fern.reportservice.service.ReportInventoryCompatibilityService;
import com.fern.reportservice.service.ReportInventoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/inventory")
@Tag(name = "Report Inventory")
public class ReportInventoryController {
    private final ReportInventoryCompatibilityService compatibilityService;
    private final ReportInventoryQueryService queryService;
    private final String inventoryProxySunset;

    public ReportInventoryController(
            ReportInventoryCompatibilityService compatibilityService,
            ReportInventoryQueryService queryService,
            @Value("${fern.report.inventory-proxy.sunset-rfc1123:Wed, 31 Dec 2026 23:59:59 GMT}") String inventoryProxySunset
    ) {
        this.compatibilityService = compatibilityService;
        this.queryService = queryService;
        this.inventoryProxySunset = inventoryProxySunset;
    }

    @Operation(summary = "Get report inventory stock balances", deprecated = true)
    @GetMapping("/stock-balances")
    public ResponseEntity<Object> getStockBalances(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam MultiValueMap<String, String> allParams,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId
    ) {
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", inventoryProxySunset)
                .body(compatibilityService.fetchStockBalances(principal, allParams, correlationId));
    }

    @Operation(summary = "Get report inventory transactions", deprecated = true)
    @GetMapping("/inventory-transactions")
    public ResponseEntity<Object> getInventoryTransactions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam MultiValueMap<String, String> allParams,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId
    ) {
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", inventoryProxySunset)
                .body(compatibilityService.fetchInventoryTransactions(principal, allParams, correlationId));
    }

    @Operation(summary = "Get inventory stock balance snapshots from reporting projection")
    @GetMapping("/stock-balance-snapshots")
    public PageResponse<InventoryStockBalanceSnapshotResponse> getStockBalanceSnapshots(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return queryService.listStockBalanceSnapshots(principal, outletId, ingredientId, page, size);
    }

    @Operation(summary = "Get inventory transaction facts from reporting projection")
    @GetMapping("/transaction-facts")
    public PageResponse<InventoryMovementFactResponse> getTransactionFacts(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) Long ingredientId,
            @RequestParam(required = false) String movementType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String sourceReferenceType,
            @RequestParam(required = false) String sourceReferenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return queryService.listTransactionFacts(
                principal,
                outletId,
                ingredientId,
                movementType,
                from,
                to,
                sourceReferenceType,
                sourceReferenceId,
                page,
                size
        );
    }
}
