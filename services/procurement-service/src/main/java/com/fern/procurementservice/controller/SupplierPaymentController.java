package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierPaymentRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import com.fern.procurementservice.service.ProcurementService;
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
    private final ProcurementService procurementService;

    public SupplierPaymentController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @PostMapping
    public SupplierPaymentResponse createSupplierPayment(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateSupplierPaymentRequest request
    ) {
        return procurementService.createSupplierPayment(principal, idempotencyKey, request);
    }
}
