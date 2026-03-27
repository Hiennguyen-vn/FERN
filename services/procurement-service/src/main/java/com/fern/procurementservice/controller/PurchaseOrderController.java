package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.procurementservice.dto.ProcurementCommands.CreatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementCommands.UpdatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderResponse;
import com.fern.procurementservice.service.ProcurementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {
    private final ProcurementService procurementService;

    public PurchaseOrderController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @PostMapping
    public PurchaseOrderResponse createPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreatePurchaseOrderRequest request
    ) {
        return procurementService.createPurchaseOrder(principal, request);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse getPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.getPurchaseOrder(principal, id);
    }

    @PatchMapping("/{id}")
    public PurchaseOrderResponse updatePurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePurchaseOrderRequest request
    ) {
        return procurementService.updatePurchaseOrder(principal, id, request);
    }

    @PostMapping("/{id}/submit")
    public PurchaseOrderResponse submitPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.submitPurchaseOrder(principal, id);
    }

    @PostMapping("/{id}/approve")
    public PurchaseOrderResponse approvePurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.approvePurchaseOrder(principal, id);
    }

    @PostMapping("/{id}/issue")
    public PurchaseOrderResponse issuePurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.issuePurchaseOrder(principal, id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancelPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.cancelPurchaseOrder(principal, id);
    }
}
