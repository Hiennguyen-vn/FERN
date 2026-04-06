package com.fern.financeservice.controller;

import com.fern.financeservice.dto.FinanceResponses.OutletCloseCheckResponse;
import com.fern.financeservice.dto.FinanceResponses.IntegrationEventReplayResponse;
import com.fern.financeservice.service.FinanceAuthorizer;
import com.fern.financeservice.service.FinanceProcurementConsumer;
import com.fern.financeservice.service.OutletCloseCheckService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/finance")
@Tag(name = "Finance — Internal")
public class InternalFinanceController {
    private final FinanceAuthorizer financeAuthorizer;
    private final OutletCloseCheckService outletCloseCheckService;
    private final FinanceProcurementConsumer financeProcurementConsumer;

    public InternalFinanceController(
            FinanceAuthorizer financeAuthorizer,
            OutletCloseCheckService outletCloseCheckService,
            FinanceProcurementConsumer financeProcurementConsumer
    ) {
        this.financeAuthorizer = financeAuthorizer;
        this.outletCloseCheckService = outletCloseCheckService;
        this.financeProcurementConsumer = financeProcurementConsumer;
    }

    @Operation(summary = "Get Finance — Internal")
    @GetMapping("/outlet-close-check")
    public OutletCloseCheckResponse outletCloseCheck(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId
    ) {
        financeAuthorizer.requireInternalPermission(principal, PermissionCodes.FINANCE_INTERNAL_READ);
        return outletCloseCheckService.getOutletCloseCheck(outletId);
    }

    @Operation(summary = "Replay failed finance integration event")
    @PostMapping("/integration-events/{sourceEventId}/replay")
    public IntegrationEventReplayResponse replayFailedIntegrationEvent(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String sourceEventId
    ) {
        financeAuthorizer.requireInternalPermission(principal, PermissionCodes.FINANCE_INTERNAL_READ);
        return financeProcurementConsumer.replayFailedIntegrationEvent(sourceEventId);
    }
}
