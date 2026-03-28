package com.fern.reportservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportResponses {
    private ReportResponses() {
    }

    public record PayrollSummaryResponse(
            Long regionId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal totalGrossPay,
            BigDecimal totalNetPay,
            BigDecimal totalTax,
            BigDecimal totalExpense,
            long runCount
    ) {
    }

    public record PayrollRunEmployeeResponse(
            Long employeeId,
            Long outletId,
            BigDecimal grossPay,
            BigDecimal netPay,
            BigDecimal taxAmount,
            LocalDate businessDate
    ) {
    }

    public record PayrollRunAllocationResponse(
            Long outletId,
            BigDecimal totalAmount
    ) {
    }

    public record PayrollRunReportResponse(
            Long payrollRunId,
            List<PayrollRunEmployeeResponse> employees,
            List<PayrollRunAllocationResponse> allocations
    ) {
    }

    public record ExportJobResponse(
            Long exportJobId,
            String status,
            String filePath,
            Instant completedAt
    ) {
    }
}
