package com.fern.reportservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import com.fern.reportservice.dto.ReportRevenueResponses.OutletTodayStatResponse;
import com.fern.reportservice.service.ReportRevenueService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports/revenue")
@Tag(name = "Report Revenue")
public class ReportRevenueController {
    private final ReportRevenueService reportRevenueService;

    public ReportRevenueController(ReportRevenueService reportRevenueService) {
        this.reportRevenueService = reportRevenueService;
    }

    @Operation(summary = "Get Report Revenue")
    @GetMapping("/outlet-stats/today")
    public List<OutletTodayStatResponse> todayStats(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam List<Long> outletIds,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId
    ) {
        return reportRevenueService.listOutletTodayStats(principal, outletIds, correlationId);
    }
}
