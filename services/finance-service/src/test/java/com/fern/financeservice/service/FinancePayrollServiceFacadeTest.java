package com.fern.financeservice.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinancePayrollServiceFacadeTest {
    @Test
    void shouldDelegateRepresentativeOperations() throws Exception {
        PayrollPeriodService payrollPeriodService = mock(PayrollPeriodService.class);
        PayrollRunOrchestrator payrollRunOrchestrator = mock(PayrollRunOrchestrator.class);
        FinanceConfigService financeConfigService = mock(FinanceConfigService.class);
        FinancePayrollService financePayrollService = new FinancePayrollService(payrollPeriodService, payrollRunOrchestrator, financeConfigService);
        FernPrincipal principal = new FernPrincipal(1L, "tester", Set.of("admin"), Set.of("a"), new ScopeRoots(true, List.of(), List.of()), 1L, 1L, "jti");
        CreatePayrollPeriodRequest createRequest = new CreatePayrollPeriodRequest(1L, "March", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 5), null);
        MarkPaidRequest markPaidRequest = new MarkPaidRequest("PAY-1", "done");
        SystemPolicyResponse policyResponse = new SystemPolicyResponse("payroll.tax", new ObjectMapper().readTree("{\"rate\":0.1}"), "Tax");
        when(financeConfigService.getSystemPolicy(principal, "payroll.tax")).thenReturn(policyResponse);

        financePayrollService.createPayrollPeriod(principal, createRequest, "corr-1");
        financePayrollService.markPayrollPaid(principal, 55L, markPaidRequest, "corr-2");
        financePayrollService.getSystemPolicy(principal, "payroll.tax");

        verify(payrollPeriodService).createPayrollPeriod(principal, createRequest, "corr-1");
        verify(payrollRunOrchestrator).markPayrollPaid(principal, 55L, markPaidRequest, "corr-2");
        verify(financeConfigService).getSystemPolicy(principal, "payroll.tax");
    }
}
