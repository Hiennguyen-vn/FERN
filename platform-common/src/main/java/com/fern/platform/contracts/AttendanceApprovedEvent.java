package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AttendanceApprovedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Long approvalId,
        Long shiftAssignmentId,
        Long employeeId,
        Long regionId,
        Long outletId,
        LocalDate businessDate,
        String attendanceStatus,
        BigDecimal workHours,
        BigDecimal overtimeHours,
        Long contractId,
        Long approvedByUserId
) {
}
