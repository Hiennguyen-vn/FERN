package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.observability.CorrelationId;
import com.fern.procurementservice.dto.ProcurementCommands.CreateGoodsReceiptRequest;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptResponse;
import com.fern.procurementservice.service.PurchaseFlowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goods-receipts")
public class GoodsReceiptController {
    private final PurchaseFlowService purchaseFlowService;

    public GoodsReceiptController(PurchaseFlowService purchaseFlowService) {
        this.purchaseFlowService = purchaseFlowService;
    }

    @GetMapping
    public List<GoodsReceiptResponse> listGoodsReceipts(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long purchaseOrderId,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit
    ) {
        return purchaseFlowService.listGoodsReceipts(principal, purchaseOrderId, outletId, status, ListQueryDefaults.clampLimit(limit));
    }

    @PostMapping
    public GoodsReceiptResponse createGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateGoodsReceiptRequest request
    ) {
        return purchaseFlowService.createGoodsReceipt(principal, request);
    }

    @GetMapping("/{id}")
    public GoodsReceiptResponse getGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.getGoodsReceipt(principal, id);
    }

    @PostMapping("/{id}/receive")
    public GoodsReceiptResponse receiveGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.receiveGoodsReceipt(principal, id);
    }

    @PostMapping("/{id}/post")
    public GoodsReceiptResponse postGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId
    ) {
        return purchaseFlowService.postGoodsReceipt(principal, id, idempotencyKey, correlationId);
    }

    @PostMapping("/{id}/cancel")
    public GoodsReceiptResponse cancelGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return purchaseFlowService.cancelGoodsReceipt(principal, id);
    }
}
