package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.procurementservice.dto.ProcurementCommands.CreatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementCommands.UpdatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderResponse;
import com.fern.procurementservice.service.PurchaseFlowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/purchase-orders")
@Tag(name = "Purchase Order")
public class PurchaseOrderController {
    private final PurchaseFlowService purchaseFlowService;

    public PurchaseOrderController(PurchaseFlowService purchaseFlowService) {
        this.purchaseFlowService = purchaseFlowService;
    }

    @Operation(summary = "Get Purchase Order")
    @GetMapping
    public List<PurchaseOrderResponse> listPurchaseOrders(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit
    ) {
        return purchaseFlowService.listPurchaseOrders(principal, outletId, supplierId, status, ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Create or execute Purchase Order")
    @PostMapping
    public PurchaseOrderResponse createPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreatePurchaseOrderRequest request
    ) {
        return purchaseFlowService.createPurchaseOrder(principal, request);
    }

    @Operation(summary = "Get Purchase Order")
    @GetMapping("/{id}")
    public PurchaseOrderResponse getPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.getPurchaseOrder(principal, id);
    }

    @Operation(summary = "Patch Purchase Order")
    @PatchMapping("/{id}")
    public PurchaseOrderResponse updatePurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePurchaseOrderRequest request
    ) {
        return purchaseFlowService.updatePurchaseOrder(principal, id, request);
    }

    @Operation(summary = "Create or execute Purchase Order")
    @PostMapping("/{id}/submit")
    public PurchaseOrderResponse submitPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.submitPurchaseOrder(principal, id);
    }

    @Operation(summary = "Create or execute Purchase Order")
    @PostMapping("/{id}/approve")
    public PurchaseOrderResponse approvePurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.approvePurchaseOrder(principal, id);
    }

    @Operation(summary = "Create or execute Purchase Order")
    @PostMapping("/{id}/issue")
    public PurchaseOrderResponse issuePurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.issuePurchaseOrder(principal, id);
    }

    @Operation(summary = "Create or execute Purchase Order")
    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponse cancelPurchaseOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.cancelPurchaseOrder(principal, id);
    }
}
