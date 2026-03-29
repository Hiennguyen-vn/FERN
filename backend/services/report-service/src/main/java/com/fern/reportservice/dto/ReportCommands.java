package com.fern.reportservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public final class ReportCommands {
    private ReportCommands() {
    }

    public record CreatePayrollExportRequest(
            @NotNull Long regionId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate
    ) {
    }

    public record CreateExportRequest(
            @NotNull String dataset,
            String format,
            Long regionId,
            Long outletId,
            LocalDate fromDate,
            LocalDate toDate,
            Long payrollRunId,
            @PositiveOrZero Integer limit
    ) {
    }
}
