package com.fern.inventoryservice.controller;

import com.fern.inventoryservice.service.StockReservationService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/internal/inventory")
@Tag(name = "Inventory — Internal")
public class InternalInventoryController {
    private final StockReservationService stockReservationService;

    public InternalInventoryController(StockReservationService stockReservationService) {
        this.stockReservationService = stockReservationService;
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
}
