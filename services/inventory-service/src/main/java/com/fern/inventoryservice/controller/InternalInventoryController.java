package com.fern.inventoryservice.controller;

import com.fern.inventoryservice.service.InventoryService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory")
public class InternalInventoryController {
    private final InventoryService inventoryService;

    public InternalInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/sale-reservations")
    public SaleReservationResponse reserveSale(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody SaleReservationRequest request
    ) {
        return inventoryService.reserveSale(principal, request);
    }
}
