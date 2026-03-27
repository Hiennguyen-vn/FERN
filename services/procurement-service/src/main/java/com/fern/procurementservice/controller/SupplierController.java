package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierUpsertRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierResponse;
import com.fern.procurementservice.service.ProcurementService;
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
    private final ProcurementService procurementService;

    public SupplierController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    @GetMapping
    public List<SupplierResponse> listSuppliers(@AuthenticationPrincipal FernPrincipal principal) {
        return procurementService.listSuppliers(principal);
    }

    @PostMapping
    public SupplierResponse createSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody SupplierUpsertRequest request
    ) {
        return procurementService.createSupplier(principal, request);
    }

    @PatchMapping("/{id}")
    public SupplierResponse updateSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody SupplierUpsertRequest request
    ) {
        return procurementService.updateSupplier(principal, id, request);
    }

    @PostMapping("/{id}/activate")
    public SupplierResponse activateSupplier(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return procurementService.activateSupplier(principal, id);
    }
}
