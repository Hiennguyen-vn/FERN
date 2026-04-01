package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.observability.CorrelationId;
import com.fern.posservice.dto.PosCommands.AddPaymentRequest;
import com.fern.posservice.dto.PosCommands.CreateSaleOrderRequest;
import com.fern.posservice.dto.PosCommands.UpdateSaleOrderRequest;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import com.fern.posservice.service.PosOrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sale-orders")
public class SaleOrderController {
    private final PosOrderService posOrderService;

    public SaleOrderController(PosOrderService posOrderService) {
        this.posOrderService = posOrderService;
    }

    @PostMapping
    public SaleOrderResponse createOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateSaleOrderRequest request
    ) {
        return posOrderService.createOrder(principal, request);
    }

    @GetMapping
    public List<SaleOrderResponse> listOrders(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long posSessionId,
            @RequestParam(required = false) Integer limit
    ) {
        return posOrderService.listOrdersBySession(principal, posSessionId,
                ListQueryDefaults.clampLimit(limit));
    }

    @GetMapping("/{id}")
    public SaleOrderResponse getOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posOrderService.getOrder(principal, id);
    }

    @GetMapping("/{id}/snapshot")
    public Map<String, Object> getSaleOrderSnapshot(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posOrderService.getSaleOrderSnapshot(principal, id);
    }

    @PatchMapping("/{id}")
    public SaleOrderResponse updateOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSaleOrderRequest request
    ) {
        return posOrderService.updateOrder(principal, id, request);
    }

    @PostMapping("/{id}/payments")
    public SaleOrderResponse addPayment(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @Valid @RequestBody AddPaymentRequest request
    ) {
        return posOrderService.addPayment(principal, id, idempotencyKey, correlationId, request);
    }

    @PostMapping("/{id}/complete")
    public SaleOrderResponse completeOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId
    ) {
        return posOrderService.completeOrder(principal, id, correlationId);
    }

    @PostMapping("/{id}/cancel")
    public SaleOrderResponse cancelOrder(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posOrderService.cancelOrder(principal, id);
    }
}
