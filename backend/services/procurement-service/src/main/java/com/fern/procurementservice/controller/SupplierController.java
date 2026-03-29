package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierUpsertRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierResponse;
import com.fern.procurementservice.service.SupplierService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/suppliers")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierResponse> listSuppliers(@AuthenticationPrincipal FernPrincipal principal) {
        return supplierService.listSuppliers(principal);
    }

    @PostMapping
    public SupplierResponse createSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody SupplierUpsertRequest request
    ) {
        return supplierService.createSupplier(principal, request);
    }

    @PatchMapping("/{id}")
    public SupplierResponse updateSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SupplierUpsertRequest request
    ) {
        return supplierService.updateSupplier(principal, id, request);
    }

    @PostMapping("/{id}/activate")
    public SupplierResponse activateSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return supplierService.activateSupplier(principal, id);
    }
}
