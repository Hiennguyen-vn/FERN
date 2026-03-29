package com.fern.financeservice.service.payroll.model;

import java.time.LocalDate;

public record PayrollPeriodRecord(
        Long id,
        Long regionId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate payDate,
        String status
) {
}
