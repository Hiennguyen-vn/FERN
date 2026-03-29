package com.fern.financeservice.service.payroll.model;

import java.math.BigDecimal;
import java.util.List;

public record PayrollEmployeeComputation(
        Long primaryContractId,
        Long primaryOutletId,
        BigDecimal grossPay,
        BigDecimal deductionAmount,
        BigDecimal taxAmount,
        BigDecimal netPay,
        BigDecimal workDays,
        BigDecimal workHours,
        BigDecimal overtimeHours,
        String exceptionMessage,
        List<PayrollLine> lines,
        List<OutletAllocation> allocations
) {
}
