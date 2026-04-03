package com.fern.inventoryservice.controller;

import com.fern.inventoryservice.dto.InventoryResponses.OutletCloseCheckResponse;
import com.fern.inventoryservice.service.InventoryAuthorizer;
import com.fern.inventoryservice.service.OutletCloseCheckService;
import com.fern.inventoryservice.service.StockReservationService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/internal/inventory")
@Tag(name = "Inventory — Internal")
public class InternalInventoryController {
    private final InventoryAuthorizer inventoryAuthorizer;
    private final StockReservationService stockReservationService;
    private final OutletCloseCheckService outletCloseCheckService;

    public InternalInventoryController(
            InventoryAuthorizer inventoryAuthorizer,
            StockReservationService stockReservationService,
            OutletCloseCheckService outletCloseCheckService
    ) {
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.stockReservationService = stockReservationService;
        this.outletCloseCheckService = outletCloseCheckService;
    }

    @Operation(summary = "Create or execute Inventory — Internal")
    @PostMapping("/sale-reservations")
    public SaleReservationResponse reserveSale(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody SaleReservationRequest request
    ) {
        return stockReservationService.reserveSale(principal, request);
    }

    @Operation(summary = "Create or execute Inventory — Internal")
    @PostMapping("/sale-reservations/{reservationId}/cancel")
    public void releaseSaleReservation(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long reservationId
    ) {
        stockReservationService.releaseSaleReservation(principal, reservationId);
    }

    @Operation(summary = "Create or execute Inventory — Internal")
    @PostMapping("/sale-reservations/by-source-order/{sourceOrderId}/cancel")
    public void releaseSaleReservationBySourceOrderId(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long sourceOrderId
    ) {
        stockReservationService.releaseSaleReservationBySourceOrderId(principal, sourceOrderId);
    }

    @Operation(summary = "Get Inventory — Internal")
    @GetMapping("/outlet-close-check")
    public OutletCloseCheckResponse outletCloseCheck(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId
    ) {
        inventoryAuthorizer.requireInternalPermission(principal, PermissionCodes.INVENTORY_INTERNAL_READ);
        return outletCloseCheckService.getOutletCloseCheck(outletId);
    }
}
