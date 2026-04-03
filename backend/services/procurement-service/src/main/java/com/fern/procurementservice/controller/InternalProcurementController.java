package com.fern.procurementservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.procurementservice.dto.ProcurementResponses.OutletCloseCheckResponse;
import com.fern.procurementservice.service.OutletCloseCheckService;
import com.fern.procurementservice.service.ProcurementAuthorizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/procurement")
@Tag(name = "Procurement — Internal")
public class InternalProcurementController {
    private final ProcurementAuthorizer procurementAuthorizer;
    private final OutletCloseCheckService outletCloseCheckService;

    public InternalProcurementController(
            ProcurementAuthorizer procurementAuthorizer,
            OutletCloseCheckService outletCloseCheckService
    ) {
        this.procurementAuthorizer = procurementAuthorizer;
        this.outletCloseCheckService = outletCloseCheckService;
    }

    @Operation(summary = "Get Procurement — Internal")
    @GetMapping("/outlet-close-check")
    public OutletCloseCheckResponse outletCloseCheck(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId
    ) {
        procurementAuthorizer.requireInternalPermission(principal, PermissionCodes.PROCUREMENT_INTERNAL_READ);
        return outletCloseCheckService.getOutletCloseCheck(outletId);
    }
}
