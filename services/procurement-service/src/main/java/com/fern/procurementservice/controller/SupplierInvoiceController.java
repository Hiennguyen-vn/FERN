package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierInvoiceRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceResponse;
import com.fern.procurementservice.service.ProcurementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/supplier-invoices")
public class SupplierInvoiceController {
    private final ProcurementService procurementService;

    public SupplierInvoiceController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @PostMapping
    public SupplierInvoiceResponse createSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateSupplierInvoiceRequest request
    ) {
        return procurementService.createSupplierInvoice(principal, request);
    }

    @GetMapping("/{id}")
    public SupplierInvoiceResponse getSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.getSupplierInvoice(principal, id);
    }

    @PostMapping("/{id}/approve")
    public SupplierInvoiceResponse approveSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.approveSupplierInvoice(principal, id);
    }

    @PostMapping("/{id}/dispute")
    public SupplierInvoiceResponse disputeSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.disputeSupplierInvoice(principal, id);
    }
}
