package com.fern.reportservice.controller;

import com.fern.reportservice.dto.ReportInventoryResponses.ProjectionFreshnessResponse;
import com.fern.reportservice.service.ReportInventoryQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/report")
public class InternalReportController {
    private final ReportInventoryQueryService reportInventoryQueryService;

    public InternalReportController(ReportInventoryQueryService reportInventoryQueryService) {
        this.reportInventoryQueryService = reportInventoryQueryService;
    }

    @GetMapping("/projection-freshness")
    public List<ProjectionFreshnessResponse> projectionFreshness(
            @RequestParam(required = false) String dataset
    ) {
        return reportInventoryQueryService.listProjectionFreshness(dataset);
    }
}
