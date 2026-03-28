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
}
