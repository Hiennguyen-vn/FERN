package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.observability.CorrelationId;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierPaymentRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import com.fern.procurementservice.service.PayablesService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/supplier-payments")
@Tag(name = "Supplier Payment")
public class SupplierPaymentController {
    private final PayablesService payablesService;

    public SupplierPaymentController(PayablesService payablesService) {
        this.payablesService = payablesService;
    }

    @Operation(summary = "Get Supplier Payment")
    @GetMapping
    public java.util.List<SupplierPaymentResponse> listSupplierPayments(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Integer limit
    ) {
        if (supplierId != null) {
            return payablesService.listSupplierPaymentsBySupplier(principal, supplierId, ListQueryDefaults.clampLimit(limit));
        }
        return payablesService.listSupplierPayments(principal, ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Create or execute Supplier Payment")
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
