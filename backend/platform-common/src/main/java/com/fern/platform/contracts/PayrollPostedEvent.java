package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PayrollPostedEvent(
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
        String paymentReference,
        Long markedPaidByUserId,
        List<PayrollExpenseLink> expenses
) {
    public static final int CURRENT_VERSION = 1;

    public PayrollPostedEvent {
        eventVersion = eventVersion == null ? CURRENT_VERSION : eventVersion;
        expenses = expenses == null ? List.of() : List.copyOf(expenses);
    }

    public PayrollPostedEvent(
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
            String paymentReference,
            Long markedPaidByUserId,
            List<PayrollExpenseLink> expenses
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
                paymentReference,
                markedPaidByUserId,
                expenses
        );
    }

    public record PayrollExpenseLink(
            Long expenseRecordId,
            Long employeeId,
            Long outletId,
            BigDecimal amount
    ) {
    }
}
