package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.posservice.dto.PosResponses.OutletTodayStatResponse;
import com.fern.posservice.service.PosStatsService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/pos-stats")
@Tag(name = "Pos Stats")
public class PosStatsController {
    private final PosStatsService posStatsService;

    public PosStatsController(PosStatsService posStatsService) {
        this.posStatsService = posStatsService;
    }

    @Operation(summary = "Get outlet revenue and session stats for the current business day")
    @GetMapping("/today")
    public List<OutletTodayStatResponse> todayStats(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam List<Long> outletIds
    ) {
        return posStatsService.listTodayStats(principal, outletIds);
    }
}
