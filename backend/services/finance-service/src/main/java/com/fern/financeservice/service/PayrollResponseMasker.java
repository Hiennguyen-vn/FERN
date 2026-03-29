package com.fern.financeservice.service;

import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PayrollResponseMasker {
    public PayrollRunResponse maskForPrincipal(FernPrincipal principal, PayrollRunResponse response) {
        if (response == null || canReadDetail(principal)) {
            return response;
        }
        return new PayrollRunResponse(
                response.id(),
                response.payrollPeriodId(),
                response.runCode(),
                response.runDate(),
                response.status(),
                response.totalAmount(),
                response.paymentRef(),
                response.note(),
                response.submittedAt(),
                response.approvedAt(),
                response.paidAt(),
                List.of()
        );
    }

    public boolean canReadDetail(FernPrincipal principal) {
        return principal != null && principal.permissions().contains(PermissionCodes.FINANCE_PAYROLL_DETAIL_READ);
    }
}
