package com.fern.reportservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.reportservice.dto.ReportInventoryResponses.InventoryMovementFactResponse;
import com.fern.reportservice.dto.ReportInventoryResponses.InventoryStockBalanceSnapshotResponse;
import com.fern.reportservice.dto.ReportInventoryResponses.ProjectionFreshnessResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportInventoryQueryService {
    private static final int MAX_PAGE_SIZE = 200;

    private final NamedParameterJdbcTemplate readJdbcTemplate;
    private final ReportAuthorizer reportAuthorizer;

    public ReportInventoryQueryService(
            @Qualifier("readJdbcTemplate") NamedParameterJdbcTemplate readJdbcTemplate,
            ReportAuthorizer reportAuthorizer
    ) {
        this.readJdbcTemplate = readJdbcTemplate;
        this.reportAuthorizer = reportAuthorizer;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryStockBalanceSnapshotResponse> listStockBalanceSnapshots(
            FernPrincipal principal,
            Long outletId,
            Long ingredientId,
            int page,
            int size
    ) {
        validatePage(page, size);
        reportAuthorizer.requireOutletReportRead(principal, outletId);

        StringBuilder sql = new StringBuilder("""
                SELECT region_id, outlet_id, ingredient_id, qty_on_hand, unit_cost, last_count_date, last_movement_at
                FROM report.inventory_stock_snapshot
                WHERE outlet_id = :outletId
                """);
        MapSqlParameterSource parameters = params("outletId", outletId);
        if (ingredientId != null) {
            sql.append("\n  AND ingredient_id = :ingredientId");
            parameters.addValue("ingredientId", ingredientId);
        }
        sql.append("\nORDER BY ingredient_id");
        sql.append("\nLIMIT :limit OFFSET :offset");
        parameters.addValue("limit", size + 1);
        parameters.addValue("offset", page * size);

        List<InventoryStockBalanceSnapshotResponse> items = readJdbcTemplate.query(
                sql.toString(),
                parameters,
                stockSnapshotMapper()
        );
        return toPageResponse(items, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementFactResponse> listTransactionFacts(
            FernPrincipal principal,
            Long outletId,
            Long ingredientId,
            String movementType,
            LocalDate from,
            LocalDate to,
            String sourceReferenceType,
            String sourceReferenceId,
            int page,
            int size
    ) {
        validatePage(page, size);
        reportAuthorizer.requireOutletReportRead(principal, outletId);

        StringBuilder sql = new StringBuilder("""
                SELECT source_event_id, region_id, outlet_id, ingredient_id, qty_change, business_date, occurred_at,
                       movement_type, unit_cost, source_reference_type, source_reference_id
                FROM report.inventory_movement_fact
                WHERE outlet_id = :outletId
                """);
        MapSqlParameterSource parameters = params("outletId", outletId);
        if (ingredientId != null) {
            sql.append("\n  AND ingredient_id = :ingredientId");
            parameters.addValue("ingredientId", ingredientId);
        }
        if (movementType != null && !movementType.isBlank()) {
            sql.append("\n  AND movement_type = :movementType");
            parameters.addValue("movementType", movementType);
        }
        if (from != null) {
            sql.append("\n  AND business_date >= :fromDate");
            parameters.addValue("fromDate", from);
        }
        if (to != null) {
            sql.append("\n  AND business_date <= :toDate");
            parameters.addValue("toDate", to);
        }
        if (sourceReferenceType != null && !sourceReferenceType.isBlank()) {
            sql.append("\n  AND source_reference_type = :sourceReferenceType");
            parameters.addValue("sourceReferenceType", sourceReferenceType);
        }
        if (sourceReferenceId != null && !sourceReferenceId.isBlank()) {
            sql.append("\n  AND source_reference_id = :sourceReferenceId");
            parameters.addValue("sourceReferenceId", sourceReferenceId);
        }
        sql.append("\nORDER BY occurred_at DESC, fact_id DESC");
        sql.append("\nLIMIT :limit OFFSET :offset");
        parameters.addValue("limit", size + 1);
        parameters.addValue("offset", page * size);

        List<InventoryMovementFactResponse> items = readJdbcTemplate.query(
                sql.toString(),
                parameters,
                movementFactMapper()
        );
        return toPageResponse(items, page, size);
    }

    @Transactional(readOnly = true)
    public List<ProjectionFreshnessResponse> listProjectionFreshness(String dataset) {
        StringBuilder sql = new StringBuilder("""
                SELECT dataset, last_occurred_at, last_ingested_at, failed_landing_count
                FROM report.projection_watermark
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (dataset != null && !dataset.isBlank()) {
            sql.append("\nWHERE dataset = :dataset");
            parameters.addValue("dataset", dataset);
        }
        sql.append("\nORDER BY dataset");
        return readJdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> {
            Instant lastOccurredAt = instant(rs, "last_occurred_at");
            Instant lastIngestedAt = instant(rs, "last_ingested_at");
            long lagMillis = lastOccurredAt == null || lastIngestedAt == null
                    ? 0L
                    : Math.max(0L, java.time.Duration.between(lastOccurredAt, lastIngestedAt).toMillis());
            return new ProjectionFreshnessResponse(
                    rs.getString("dataset"),
                    lastOccurredAt,
                    lastIngestedAt,
                    lagMillis,
                    rs.getLong("failed_landing_count")
            );
        });
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            parameters.addValue((String) values[index], values[index + 1]);
        }
        return parameters;
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page cannot be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Page size must be greater than 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size cannot exceed 200");
        }
    }

    private <T> PageResponse<T> toPageResponse(List<T> items, int page, int size) {
        boolean hasMore = items.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(items.subList(0, size)) : items;
        return new PageResponse<>(pagedItems, page, size, hasMore);
    }

    private RowMapper<InventoryStockBalanceSnapshotResponse> stockSnapshotMapper() {
        return (resultSet, rowNum) -> new InventoryStockBalanceSnapshotResponse(
                resultSet.getLong("region_id"),
                resultSet.getLong("outlet_id"),
                resultSet.getLong("ingredient_id"),
                resultSet.getBigDecimal("qty_on_hand"),
                resultSet.getBigDecimal("unit_cost"),
                resultSet.getObject("last_count_date", LocalDate.class),
                instant(resultSet, "last_movement_at")
        );
    }

    private RowMapper<InventoryMovementFactResponse> movementFactMapper() {
        return (resultSet, rowNum) -> new InventoryMovementFactResponse(
                resultSet.getString("source_event_id"),
                resultSet.getLong("region_id"),
                resultSet.getLong("outlet_id"),
                resultSet.getLong("ingredient_id"),
                resultSet.getBigDecimal("qty_change"),
                resultSet.getObject("business_date", LocalDate.class),
                instant(resultSet, "occurred_at"),
                resultSet.getString("movement_type"),
                resultSet.getBigDecimal("unit_cost"),
                resultSet.getString("source_reference_type"),
                resultSet.getString("source_reference_id")
        );
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
