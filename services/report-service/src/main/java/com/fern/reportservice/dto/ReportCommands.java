package com.fern.reportservice.dto;

import jakarta.validation.constraints.NotNull;
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
}
