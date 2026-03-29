package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpensePostedEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String sourceService,
        String correlationId,
        String idempotencyKey,
        Integer eventVersion,
        Long expenseRecordId,
        Long regionId,
        Long outletId,
        Long employeeId,
        Long payrollRunId,
        LocalDate businessDate,
        String sourceType,
        BigDecimal amount,
        String sourceReferenceType,
        String sourceReferenceId
) {
    public static final int CURRENT_VERSION = 1;

    public ExpensePostedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
    }

    public ExpensePostedEvent(
            String eventId,
            String eventType,
            Instant occurredAt,
            String sourceService,
            String correlationId,
            String idempotencyKey,
            Long expenseRecordId,
            Long regionId,
            Long outletId,
            Long employeeId,
            Long payrollRunId,
            LocalDate businessDate,
            String sourceType,
            BigDecimal amount,
            String sourceReferenceType,
            String sourceReferenceId
    ) {
        this(
                eventId,
                eventType,
                occurredAt,
                sourceService,
                correlationId,
                idempotencyKey,
                CURRENT_VERSION,
                expenseRecordId,
                regionId,
                outletId,
                employeeId,
                payrollRunId,
                businessDate,
                sourceType,
                amount,
                sourceReferenceType,
                sourceReferenceId
        );
    }
}
