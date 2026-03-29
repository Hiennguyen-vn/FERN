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
        Integer eventVersion,
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
    public static final int CURRENT_VERSION = 1;

    public AttendanceApprovedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
    }

    public AttendanceApprovedEvent(
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
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                approvalId,
                shiftAssignmentId,
                employeeId,
                regionId,
                outletId,
                businessDate,
                attendanceStatus,
                workHours,
                overtimeHours,
                contractId,
                approvedByUserId
        );
    }
}
