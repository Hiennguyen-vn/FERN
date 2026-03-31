package com.fern.financeservice.controller;

import com.fern.financeservice.dto.FinanceResponses.NumberingRuleResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollPeriodResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.financeservice.service.FinancePayrollService;
import com.fern.financeservice.service.PayrollResponseMasker;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class FinanceReadController {
    private final FinancePayrollService financePayrollService;
    private final PayrollResponseMasker payrollResponseMasker;

    public FinanceReadController(FinancePayrollService financePayrollService, PayrollResponseMasker payrollResponseMasker) {
        this.financePayrollService = financePayrollService;
        this.payrollResponseMasker = payrollResponseMasker;
    }

    @GetMapping("/payroll-periods")
    public List<PayrollPeriodResponse> listPayrollPeriods(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long regionId
    ) {
        return financePayrollService.listPayrollPeriods(principal, regionId);
    }

    @GetMapping("/payroll-periods/{id}")
    public PayrollPeriodResponse getPayrollPeriod(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return financePayrollService.getPayrollPeriod(principal, id);
    }

    @GetMapping("/payroll-runs")
    public List<PayrollRunResponse> listPayrollRuns(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Integer limit
    ) {
        return financePayrollService.listPayrollRuns(principal, regionId, ListQueryDefaults.clampLimit(limit)).stream()
                .map(response -> payrollResponseMasker.maskForPrincipal(principal, response))
                .toList();
    }

    @GetMapping("/payroll-runs/{id}")
    public PayrollRunResponse getPayrollRun(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return payrollResponseMasker.maskForPrincipal(principal, financePayrollService.getPayrollRun(principal, id));
    }

    @GetMapping("/finance-config/numbering-rules/{documentType}")
    public NumberingRuleResponse getNumberingRule(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String documentType
    ) {
        return financePayrollService.getNumberingRule(principal, documentType);
    }

    @GetMapping("/finance-config/system-policies/{policyKey}")
    public SystemPolicyResponse getSystemPolicy(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable String policyKey
    ) {
        return financePayrollService.getSystemPolicy(principal, policyKey);
    }
}
