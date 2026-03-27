package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.posservice.dto.PosCommands.AddPaymentRequest;
import com.fern.posservice.dto.PosCommands.CreateSaleOrderRequest;
import com.fern.posservice.dto.PosCommands.UpdateSaleOrderRequest;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import com.fern.posservice.service.PosService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sale-orders")
public class SaleOrderController {
    private final PosService posService;

    public SaleOrderController(PosService posService) {
        this.posService = posService;
    }

    @PostMapping
    public SaleOrderResponse createOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateSaleOrderRequest request
    ) {
        return posService.createOrder(principal, request);
    }

    @GetMapping("/{id}")
    public SaleOrderResponse getOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posService.getOrder(principal, id);
    }

    @PatchMapping("/{id}")
    public SaleOrderResponse updateOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSaleOrderRequest request
    ) {
        return posService.updateOrder(principal, id, request);
    }

    @PostMapping("/{id}/payments")
    public SaleOrderResponse addPayment(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AddPaymentRequest request
    ) {
        return posService.addPayment(principal, id, idempotencyKey, request);
    }

    @PostMapping("/{id}/complete")
    public SaleOrderResponse completeOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posService.completeOrder(principal, id);
    }

    @PostMapping("/{id}/cancel")
    public SaleOrderResponse cancelOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posService.cancelOrder(principal, id);
    }
}
