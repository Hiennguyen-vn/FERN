package com.fern.reportservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
            String dataset,
            String format,
            Instant requestedAt,
            Instant startedAt,
            Instant completedAt,
            Instant failedAt,
            Long rowCount,
            String downloadUrl,
            Instant expiresAt,
            String errorMessage,
            String filePath,
            List<Map<String, Object>> preview
    ) {
    }

    public record ExportPreviewResponse(
            Long exportJobId,
            String status,
            String dataset,
            Long rowCount,
            List<Map<String, Object>> rows
    ) {
    }
}
