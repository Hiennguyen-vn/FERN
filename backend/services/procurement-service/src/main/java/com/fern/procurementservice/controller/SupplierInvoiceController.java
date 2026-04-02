package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierInvoiceRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceResponse;
import com.fern.procurementservice.service.PayablesService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/supplier-invoices")
@Tag(name = "Supplier Invoice")
public class SupplierInvoiceController {
    private final PayablesService payablesService;

    public SupplierInvoiceController(PayablesService payablesService) {
        this.payablesService = payablesService;
    }

    @Operation(summary = "Get Supplier Invoice")
    @GetMapping
    public List<SupplierInvoiceResponse> listSupplierInvoices(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit
    ) {
        return payablesService.listSupplierInvoices(principal, supplierId, outletId, status, ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Create or execute Supplier Invoice")
    @PostMapping
    public SupplierInvoiceResponse createSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateSupplierInvoiceRequest request
    ) {
        return payablesService.createSupplierInvoice(principal, request);
    }

    @Operation(summary = "Get Supplier Invoice")
    @GetMapping("/{id}")
    public SupplierInvoiceResponse getSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return payablesService.getSupplierInvoice(principal, id);
    }

    @Operation(summary = "Create or execute Supplier Invoice")
    @PostMapping("/{id}/approve")
    public SupplierInvoiceResponse approveSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return payablesService.approveSupplierInvoice(principal, id);
    }

    @Operation(summary = "Create or execute Supplier Invoice")
    @PostMapping("/{id}/dispute")
    public SupplierInvoiceResponse disputeSupplierInvoice(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return payablesService.disputeSupplierInvoice(principal, id);
    }
}
