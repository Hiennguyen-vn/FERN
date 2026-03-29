package com.fern.financeservice.service.payroll.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollRunRecord(
        Long id,
        Long payrollPeriodId,
        LocalDate runDate,
        String status,
        BigDecimal totalAmount
) {
}
