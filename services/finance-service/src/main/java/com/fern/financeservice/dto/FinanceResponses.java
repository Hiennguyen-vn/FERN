package com.fern.financeservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceResponses {
    private FinanceResponses() {
    }

    public record PayrollPeriodResponse(
            Long id,
            Long regionId,
            String referenceCode,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate payDate,
            String status,
            String note
    ) {
    }

    public record PayrollLineResponse(
            Long id,
            String lineType,
            String description,
            BigDecimal amount
    ) {
    }

    public record PayrollAllocationResponse(
            Long id,
            Long outletId,
            BigDecimal workHours,
            BigDecimal allocatedAmount
    ) {
    }

    public record PayrollEmployeeResultResponse(
            Long id,
            Long employeeId,
            Long contractId,
            Long outletId,
            BigDecimal grossPay,
            BigDecimal deductionAmount,
            BigDecimal taxAmount,
            BigDecimal netPay,
            BigDecimal workDays,
            BigDecimal workHours,
            BigDecimal overtimeHours,
            String paymentStatus,
            String exceptionMessage,
            List<PayrollLineResponse> lines,
            List<PayrollAllocationResponse> allocations
    ) {
    }

    public record PayrollRunResponse(
            Long id,
            Long payrollPeriodId,
            String runCode,
            LocalDate runDate,
            String status,
            BigDecimal totalAmount,
            String paymentRef,
            String note,
            Instant submittedAt,
            Instant approvedAt,
            Instant paidAt,
            List<PayrollEmployeeResultResponse> employees
    ) {
    }

    public record NumberingRuleResponse(
            Long id,
            String documentType,
            String prefix,
            Long regionId,
            Long outletId,
            Long nextNumber,
            String resetPeriod,
            String formatPattern,
            boolean active
    ) {
    }

    public record SystemPolicyResponse(
            String policyKey,
            JsonNode policyValue,
            String description
    ) {
    }
}
