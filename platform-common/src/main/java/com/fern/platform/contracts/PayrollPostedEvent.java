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
        Long payrollRunId,
        Long payrollPeriodId,
        Long regionId,
        LocalDate businessDate,
        BigDecimal totalAmount,
        String paymentReference,
        Long markedPaidByUserId,
        List<PayrollExpenseLink> expenses
) {
    public PayrollPostedEvent {
        expenses = expenses == null ? List.of() : List.copyOf(expenses);
    }

    public record PayrollExpenseLink(
            Long expenseRecordId,
            Long employeeId,
            Long outletId,
            BigDecimal amount
    ) {
    }
}
