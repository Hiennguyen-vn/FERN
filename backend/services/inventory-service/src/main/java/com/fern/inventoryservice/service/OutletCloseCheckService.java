package com.fern.inventoryservice.service;

import com.fern.inventoryservice.dto.InventoryResponses.OutletCloseCheckResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OutletCloseCheckService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OutletCloseCheckService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OutletCloseCheckResponse getOutletCloseCheck(Long outletId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("outletId", outletId);
        Long blockingReservations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_reservation
                WHERE outlet_id = :outletId
                  AND status = 'RESERVED'
                """, parameters, Long.class);
        Long blockingStockCountSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_count_session
                WHERE outlet_id = :outletId
                  AND status NOT IN ('POSTED', 'CANCELLED')
                """, parameters, Long.class);
        long safeBlockingReservations = blockingReservations == null ? 0L : blockingReservations;
        long safeBlockingStockCountSessions = blockingStockCountSessions == null ? 0L : blockingStockCountSessions;
        return new OutletCloseCheckResponse(
                outletId,
                safeBlockingReservations,
                safeBlockingStockCountSessions,
                safeBlockingReservations > 0 || safeBlockingStockCountSessions > 0
        );
    }
}
