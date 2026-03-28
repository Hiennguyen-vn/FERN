package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierPaymentRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import com.fern.procurementservice.service.PayablesService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/supplier-payments")
public class SupplierPaymentController {
    private final PayablesService payablesService;

    public SupplierPaymentController(PayablesService payablesService) {
        this.payablesService = payablesService;
    }

    @PostMapping
    public SupplierPaymentResponse createSupplierPayment(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @Valid @RequestBody CreateSupplierPaymentRequest request
    ) {
        return payablesService.createSupplierPayment(principal, idempotencyKey, correlationId, request);
    }
}
