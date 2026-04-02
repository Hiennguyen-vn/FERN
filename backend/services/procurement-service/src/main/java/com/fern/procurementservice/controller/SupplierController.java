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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/suppliers")
@Tag(name = "Supplier")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Operation(summary = "Get Supplier")
    @GetMapping
    public List<SupplierResponse> listSuppliers(@AuthenticationPrincipal FernPrincipal principal) {
        return supplierService.listSuppliers(principal);
    }

    @Operation(summary = "Create or execute Supplier")
    @PostMapping
    public SupplierResponse createSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody SupplierUpsertRequest request
    ) {
        return supplierService.createSupplier(principal, request);
    }

    @Operation(summary = "Patch Supplier")
    @PatchMapping("/{id}")
    public SupplierResponse updateSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SupplierUpsertRequest request
    ) {
        return supplierService.updateSupplier(principal, id, request);
    }

    @Operation(summary = "Create or execute Supplier")
    @PostMapping("/{id}/activate")
    public SupplierResponse activateSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return supplierService.activateSupplier(principal, id);
    }
}
