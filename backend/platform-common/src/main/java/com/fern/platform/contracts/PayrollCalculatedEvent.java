package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PayrollCalculatedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long payrollRunId,
        Long payrollPeriodId,
        Long regionId,
        LocalDate businessDate,
        BigDecimal totalAmount,
        Long approvedByUserId,
        List<PayrollCalculatedEmployee> employees,
        List<PayrollAllocation> allocations
) {
    public static final int CURRENT_VERSION = 1;

    public PayrollCalculatedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        employees = employees == null ? List.of() : List.copyOf(employees);
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }

    public PayrollCalculatedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long payrollRunId,
            Long payrollPeriodId,
            Long regionId,
            LocalDate businessDate,
            BigDecimal totalAmount,
            Long approvedByUserId,
            List<PayrollCalculatedEmployee> employees,
            List<PayrollAllocation> allocations
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                payrollRunId,
                payrollPeriodId,
                regionId,
                businessDate,
                totalAmount,
                approvedByUserId,
                employees,
                allocations
        );
    }

    public record PayrollCalculatedEmployee(
            Long employeeId,
            Long outletId,
            BigDecimal grossPay,
            BigDecimal deductionAmount,
            BigDecimal taxAmount,
            BigDecimal netPay
    ) {
    }

    public record PayrollAllocation(
            Long employeeId,
            Long outletId,
            BigDecimal workHours,
            BigDecimal allocatedAmount
    ) {
    }
}
