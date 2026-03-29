package com.fern.financeservice.service.payroll.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EffectiveContract(
        Long contractId,
        Long employeeId,
        Long regionId,
        String employmentType,
        String salaryType,
        BigDecimal baseSalary,
        String taxCode,
        LocalDate startDate,
        LocalDate endDate
) {
}
