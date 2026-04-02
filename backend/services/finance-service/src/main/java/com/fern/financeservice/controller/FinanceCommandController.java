package com.fern.financeservice.controller;

import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollRunRequest;
import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.dto.FinanceCommands.PutNumberingRuleRequest;
import com.fern.financeservice.dto.FinanceCommands.PutSystemPolicyRequest;
import com.fern.financeservice.dto.FinanceCommands.ReviewPayrollRequest;
import com.fern.financeservice.dto.FinanceResponses.NumberingRuleResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollPeriodResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.financeservice.service.FinancePayrollService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "Finance — Commands")
public class FinanceCommandController {
    private final FinancePayrollService financePayrollService;

    public FinanceCommandController(FinancePayrollService financePayrollService) {
        this.financePayrollService = financePayrollService;
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-periods")
    public PayrollPeriodResponse createPayrollPeriod(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @Valid @RequestBody CreatePayrollPeriodRequest request
    ) {
        return financePayrollService.createPayrollPeriod(principal, request, correlationId);
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-runs")
    public PayrollRunResponse createPayrollRun(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @Valid @RequestBody CreatePayrollRunRequest request
    ) {
        return financePayrollService.createPayrollRun(principal, request, correlationId);
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-runs/{id}/submit")
    public PayrollRunResponse submitPayrollRun(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @RequestBody(required = false) ReviewPayrollRequest request
    ) {
        return financePayrollService.submitPayrollRun(principal, id, request == null ? null : request.note(), correlationId);
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-runs/{id}/approve")
    public PayrollRunResponse approvePayrollRun(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @RequestBody(required = false) ReviewPayrollRequest request
    ) {
        return financePayrollService.approvePayrollRun(principal, id, request == null ? null : request.note(), correlationId);
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-runs/{id}/reject")
    public PayrollRunResponse rejectPayrollRun(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @RequestBody(required = false) ReviewPayrollRequest request
    ) {
        return financePayrollService.rejectPayrollRun(principal, id, request == null ? null : request.note(), correlationId);
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-runs/{id}/cancel")
    public PayrollRunResponse cancelPayrollRun(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @RequestBody(required = false) ReviewPayrollRequest request
    ) {
        return financePayrollService.cancelPayrollRun(principal, id, request == null ? null : request.note(), correlationId);
    }

    @Operation(summary = "Create or execute Finance — Commands")
    @PostMapping("/payroll-runs/{id}/mark-paid")
    public PayrollRunResponse markPayrollPaid(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @Valid @RequestBody MarkPaidRequest request
    ) {
        return financePayrollService.markPayrollPaid(principal, id, request, correlationId);
    }

    @Operation(summary = "Update Finance — Commands")
    @PutMapping("/finance-config/numbering-rules/{documentType}")
    public NumberingRuleResponse putNumberingRule(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String documentType,
            @RequestBody PutNumberingRuleRequest request
    ) {
        return financePayrollService.putNumberingRule(principal, documentType, request);
    }

    @Operation(summary = "Update Finance — Commands")
    @PutMapping("/finance-config/system-policies/{policyKey}")
    public SystemPolicyResponse putSystemPolicy(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String policyKey,
            @Valid @RequestBody PutSystemPolicyRequest request
    ) {
        return financePayrollService.putSystemPolicy(principal, policyKey, request);
    }
}
