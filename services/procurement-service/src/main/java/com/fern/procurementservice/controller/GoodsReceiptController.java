package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.procurementservice.dto.ProcurementCommands.CreateGoodsReceiptRequest;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptResponse;
import com.fern.procurementservice.service.ProcurementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goods-receipts")
public class GoodsReceiptController {
    private final ProcurementService procurementService;

    public GoodsReceiptController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @PostMapping
    public GoodsReceiptResponse createGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateGoodsReceiptRequest request
    ) {
        return procurementService.createGoodsReceipt(principal, request);
    }

    @GetMapping("/{id}")
    public GoodsReceiptResponse getGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.getGoodsReceipt(principal, id);
    }

    @PostMapping("/{id}/receive")
    public GoodsReceiptResponse receiveGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.receiveGoodsReceipt(principal, id);
    }

    @PostMapping("/{id}/post")
    public GoodsReceiptResponse postGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return procurementService.postGoodsReceipt(principal, id, idempotencyKey);
    }

    @PostMapping("/{id}/cancel")
    public GoodsReceiptResponse cancelGoodsReceipt(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.cancelGoodsReceipt(principal, id);
    }
}
