package com.fern.reportservice.dto;

import java.math.BigDecimal;

public final class ReportRevenueResponses {
    private ReportRevenueResponses() {
    }

    public record OutletTodayStatResponse(
            Long outletId,
            Long sessionId,
            String sessionStatus,
            String currencyCode,
            Long totalOrders,
            Long completed,
            Long open,
            Long cancelled,
            BigDecimal totalRevenue,
            BigDecimal cashCollected,
            BigDecimal nonCashCollected
    ) {
    }
}
