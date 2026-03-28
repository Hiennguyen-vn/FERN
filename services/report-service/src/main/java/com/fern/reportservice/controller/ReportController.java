package com.fern.reportservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.reportservice.dto.ReportCommands.CreatePayrollExportRequest;
import com.fern.reportservice.dto.ReportResponses.ExportJobResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunReportResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollSummaryResponse;
import com.fern.reportservice.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/payroll")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public PayrollSummaryResponse summary(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long regionId,
            @RequestParam java.time.LocalDate fromDate,
            @RequestParam java.time.LocalDate toDate
    ) {
        return reportService.payrollSummary(principal, regionId, fromDate, toDate);
    }

    @GetMapping("/runs/{runId}")
    public PayrollRunReportResponse payrollRun(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long runId
    ) {
        return reportService.payrollRun(principal, runId);
    }

    @PostMapping("/export")
    public ExportJobResponse export(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePayrollExportRequest request
    ) {
        return reportService.createPayrollExport(principal, request, idempotencyKey);
    }
}
