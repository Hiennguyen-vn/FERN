package com.fern.reportservice.service;

import com.fern.platform.common.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Projects region-level and company-level daily summaries from domain events.
 *
 * <p>Each event projector calls {@link #applyDelta} after writing its domain-specific
 * fact table rows. This class handles the UPSERT into {@code region_daily_summary}
 * and {@code company_daily_summary}, as well as the deduplication helper tables
 * ({@code region_daily_event}, {@code company_daily_outlet}).
 *
 * <p>Extracted from the original {@code ReportService} God class.
 */
@Component
public class DailySummaryProjector {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReportIngestionSupport support;
    private final SnowflakeIdGenerator idGenerator;

    public DailySummaryProjector(
            NamedParameterJdbcTemplate jdbcTemplate,
            ReportIngestionSupport support,
            SnowflakeIdGenerator idGenerator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.support = support;
        this.idGenerator = idGenerator;
    }

    public void applyDelta(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            Long regionId,
            List<Long> outletIds,
            LocalDate businessDate,
            String payload,
            SummaryDelta delta
    ) {
        long transactionIncrement = registerRegionEvent(regionId, businessDate, sourceEventId);
        jdbcTemplate.update("""
                INSERT INTO report.region_daily_summary (
                    summary_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, business_date, total_sales, total_procurement, total_expense, total_payroll, transaction_count, payload
                ) VALUES (
                    :summaryId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :businessDate, :totalSales, :totalProcurement, :totalExpense, :totalPayroll, :transactionCount, CAST(:payload AS jsonb)
                )
                ON CONFLICT (region_id, business_date) DO UPDATE
                SET source_event_id = EXCLUDED.source_event_id,
                    source_service = EXCLUDED.source_service,
                    event_type = EXCLUDED.event_type,
                    occurred_at = EXCLUDED.occurred_at,
                    ingested_at = CURRENT_TIMESTAMP,
                    idempotency_key = EXCLUDED.idempotency_key,
                    total_sales = report.region_daily_summary.total_sales + EXCLUDED.total_sales,
                    total_procurement = report.region_daily_summary.total_procurement + EXCLUDED.total_procurement,
                    total_expense = report.region_daily_summary.total_expense + EXCLUDED.total_expense,
                    total_payroll = report.region_daily_summary.total_payroll + EXCLUDED.total_payroll,
                    transaction_count = report.region_daily_summary.transaction_count + EXCLUDED.transaction_count,
                    payload = EXCLUDED.payload
                """, support.params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey + ":region-summary",
                "regionId", regionId,
                "businessDate", businessDate,
                "totalSales", delta.totalSales(),
                "totalProcurement", delta.totalProcurement(),
                "totalExpense", delta.totalExpense(),
                "totalPayroll", delta.totalPayroll(),
                "transactionCount", transactionIncrement,
                "payload", payload
        ));
        long outletCountIncrement = registerCompanyOutlets(businessDate, outletIds);
        jdbcTemplate.update("""
                INSERT INTO report.company_daily_summary (
                    summary_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    business_date, total_sales, total_procurement, total_expense, total_payroll, outlet_count, payload
                ) VALUES (
                    :summaryId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :businessDate, :totalSales, :totalProcurement, :totalExpense, :totalPayroll, :outletCount, CAST(:payload AS jsonb)
                )
                ON CONFLICT (business_date) DO UPDATE
                SET source_event_id = EXCLUDED.source_event_id,
                    source_service = EXCLUDED.source_service,
                    event_type = EXCLUDED.event_type,
                    occurred_at = EXCLUDED.occurred_at,
                    ingested_at = CURRENT_TIMESTAMP,
                    idempotency_key = EXCLUDED.idempotency_key,
                    total_sales = report.company_daily_summary.total_sales + EXCLUDED.total_sales,
                    total_procurement = report.company_daily_summary.total_procurement + EXCLUDED.total_procurement,
                    total_expense = report.company_daily_summary.total_expense + EXCLUDED.total_expense,
                    total_payroll = report.company_daily_summary.total_payroll + EXCLUDED.total_payroll,
                    outlet_count = report.company_daily_summary.outlet_count + EXCLUDED.outlet_count,
                    payload = EXCLUDED.payload
                """, support.params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey + ":company-summary",
                "businessDate", businessDate,
                "totalSales", delta.totalSales(),
                "totalProcurement", delta.totalProcurement(),
                "totalExpense", delta.totalExpense(),
                "totalPayroll", delta.totalPayroll(),
                "outletCount", outletCountIncrement,
                "payload", payload
        ));
    }

    private long registerRegionEvent(Long regionId, LocalDate businessDate, String sourceEventId) {
        return jdbcTemplate.update("""
                INSERT INTO report.region_daily_event (
                    region_id, business_date, source_event_id
                ) VALUES (
                    :regionId, :businessDate, :sourceEventId
                )
                ON CONFLICT DO NOTHING
                """, support.params(
                "regionId", regionId,
                "businessDate", businessDate,
                "sourceEventId", sourceEventId
        ));
    }

    private long registerCompanyOutlets(LocalDate businessDate, List<Long> outletIds) {
        if (outletIds == null || outletIds.isEmpty()) {
            return 0;
        }
        long inserted = 0;
        for (Long outletId : outletIds.stream().filter(item -> item != null).distinct().toList()) {
            inserted += jdbcTemplate.update("""
                    INSERT INTO report.company_daily_outlet (
                        business_date, outlet_id
                    ) VALUES (
                        :businessDate, :outletId
                    )
                    ON CONFLICT DO NOTHING
                    """, support.params(
                    "businessDate", businessDate,
                    "outletId", outletId
            ));
        }
        return inserted;
    }

    public record SummaryDelta(
            BigDecimal totalSales,
            BigDecimal totalProcurement,
            BigDecimal totalExpense,
            BigDecimal totalPayroll,
            long transactionCount
    ) {
    }
}
