package com.fern.financeservice.service;

import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollRunRequest;
import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.dto.FinanceCommands.PutNumberingRuleRequest;
import com.fern.financeservice.dto.FinanceCommands.PutSystemPolicyRequest;
import com.fern.financeservice.dto.FinanceResponses.NumberingRuleResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollPeriodResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.platform.common.FernPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FinancePayrollService {
    private final PayrollPeriodService payrollPeriodService;
    private final PayrollRunOrchestrator payrollRunOrchestrator;
    private final FinanceConfigService financeConfigService;

    public FinancePayrollService(
            PayrollPeriodService payrollPeriodService,
            PayrollRunOrchestrator payrollRunOrchestrator,
            FinanceConfigService financeConfigService
    ) {
        this.payrollPeriodService = payrollPeriodService;
        this.payrollRunOrchestrator = payrollRunOrchestrator;
        this.financeConfigService = financeConfigService;
    }

    public PayrollPeriodResponse createPayrollPeriod(FernPrincipal principal, CreatePayrollPeriodRequest request) {
        return payrollPeriodService.createPayrollPeriod(principal, request);
    }

    public PayrollPeriodResponse createPayrollPeriod(FernPrincipal principal, CreatePayrollPeriodRequest request, String correlationId) {
        return payrollPeriodService.createPayrollPeriod(principal, request, correlationId);
    }

    public PayrollRunResponse createPayrollRun(FernPrincipal principal, CreatePayrollRunRequest request) {
        return payrollRunOrchestrator.createPayrollRun(principal, request);
    }

    public PayrollRunResponse createPayrollRun(FernPrincipal principal, CreatePayrollRunRequest request, String correlationId) {
        return payrollRunOrchestrator.createPayrollRun(principal, request, correlationId);
    }

    public PayrollRunResponse submitPayrollRun(FernPrincipal principal, Long runId, String note) {
        return payrollRunOrchestrator.submitPayrollRun(principal, runId, note);
    }

    public PayrollRunResponse submitPayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        return payrollRunOrchestrator.submitPayrollRun(principal, runId, note, correlationId);
    }

    public PayrollRunResponse approvePayrollRun(FernPrincipal principal, Long runId, String note) {
        return payrollRunOrchestrator.approvePayrollRun(principal, runId, note);
    }

    public PayrollRunResponse approvePayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        return payrollRunOrchestrator.approvePayrollRun(principal, runId, note, correlationId);
    }

    public PayrollRunResponse rejectPayrollRun(FernPrincipal principal, Long runId, String note) {
        return payrollRunOrchestrator.rejectPayrollRun(principal, runId, note);
    }

    public PayrollRunResponse rejectPayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        return payrollRunOrchestrator.rejectPayrollRun(principal, runId, note, correlationId);
    }

    public PayrollRunResponse markPayrollPaid(FernPrincipal principal, Long runId, MarkPaidRequest request) {
        return payrollRunOrchestrator.markPayrollPaid(principal, runId, request);
    }

    public PayrollRunResponse markPayrollPaid(FernPrincipal principal, Long runId, MarkPaidRequest request, String correlationId) {
        return payrollRunOrchestrator.markPayrollPaid(principal, runId, request, correlationId);
    }

    public PayrollRunResponse cancelPayrollRun(FernPrincipal principal, Long runId, String note) {
        return payrollRunOrchestrator.cancelPayrollRun(principal, runId, note);
    }

    public PayrollRunResponse cancelPayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        return payrollRunOrchestrator.cancelPayrollRun(principal, runId, note, correlationId);
    }

    public List<PayrollPeriodResponse> listPayrollPeriods(FernPrincipal principal, Long regionId) {
        return payrollPeriodService.listPayrollPeriods(principal, regionId);
    }

    public PayrollPeriodResponse getPayrollPeriod(FernPrincipal principal, Long id) {
        return payrollPeriodService.getPayrollPeriod(principal, id);
    }

    public List<PayrollRunResponse> listPayrollRuns(FernPrincipal principal, Long regionId) {
        return payrollRunOrchestrator.listPayrollRuns(principal, regionId);
    }

    public List<PayrollRunResponse> listPayrollRuns(FernPrincipal principal, Long regionId, int limit) {
        return payrollRunOrchestrator.listPayrollRuns(principal, regionId, limit);
    }

    public PayrollRunResponse getPayrollRun(FernPrincipal principal, Long id) {
        return payrollRunOrchestrator.getPayrollRun(principal, id);
    }

    public NumberingRuleResponse putNumberingRule(FernPrincipal principal, String documentType, PutNumberingRuleRequest request) {
        return financeConfigService.putNumberingRule(principal, documentType, request);
    }

    public NumberingRuleResponse getNumberingRule(FernPrincipal principal, String documentType) {
        return financeConfigService.getNumberingRule(principal, documentType);
    }

    public SystemPolicyResponse putSystemPolicy(FernPrincipal principal, String policyKey, PutSystemPolicyRequest request) {
        return financeConfigService.putSystemPolicy(principal, policyKey, request);
    }

    public SystemPolicyResponse getSystemPolicy(FernPrincipal principal, String policyKey) {
        return financeConfigService.getSystemPolicy(principal, policyKey);
    }
}
