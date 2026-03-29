package com.fern.financeservice.service.payroll.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApprovedAttendance(
        Long approvalId,
        Long shiftAssignmentId,
        Long employeeId,
        Long regionId,
        Long outletId,
        Long contractId,
        LocalDate businessDate,
        String attendanceStatus,
        BigDecimal workHours,
        BigDecimal overtimeHours
) {
}
