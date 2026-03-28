package com.fern.inventoryservice.service;

import com.fern.inventoryservice.dto.InventoryResponses.InventoryTransactionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockBalanceResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.PermissionCodes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private static final int MAX_PAGE_SIZE = 200;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;

    public InventoryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockBalanceResponse> listStockBalances(
            FernPrincipal principal,
            Long outletId,
            Long ingredientId,
            int page,
            int size
    ) {
        validatePage(page, size);
        inventoryAuthorizer.requireOutletAccess(principal, outletId, PermissionCodes.INVENTORY_BALANCE_READ);
        StringBuilder sql = new StringBuilder("""
                SELECT region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, last_count_date
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId
                """);
        MapSqlParameterSource parameters = inventoryRepository.params("outletId", outletId);
        if (ingredientId != null) {
            sql.append("\n  AND ingredient_id = :ingredientId");
            parameters.addValue("ingredientId", ingredientId);
        }
        sql.append("\nORDER BY ingredient_id");
        sql.append("\nLIMIT :limit OFFSET :offset");
        parameters.addValue("limit", size + 1);
        parameters.addValue("offset", page * size);
        List<StockBalanceResponse> items = jdbcTemplate.query(
                sql.toString(),
                parameters,
                stockBalanceMapper()
        );
        return toPageResponse(items, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> listInventoryTransactions(
            FernPrincipal principal,
            Long outletId,
            Long ingredientId,
            String txnType,
            LocalDate from,
            LocalDate to,
            String sourceType,
            String sourceId,
            int page,
            int size
    ) {
        validatePage(page, size);
        inventoryAuthorizer.requireOutletAccess(principal, outletId, PermissionCodes.INVENTORY_LEDGER_READ);
        StringBuilder sql = new StringBuilder("""
                SELECT id, region_id, outlet_id, ingredient_id, qty_change, business_date, txn_time, txn_type,
                       unit_cost, source_reference_type, source_reference_id, created_by_user_id
                FROM inventory.inventory_transaction
                WHERE outlet_id = :outletId
                """);
        MapSqlParameterSource parameters = inventoryRepository.params("outletId", outletId);
        if (ingredientId != null) {
            sql.append("\n  AND ingredient_id = :ingredientId");
            parameters.addValue("ingredientId", ingredientId);
        }
        if (txnType != null) {
            sql.append("\n  AND txn_type = :txnType");
            parameters.addValue("txnType", txnType);
        }
        if (from != null) {
            sql.append("\n  AND business_date >= :fromDate");
            parameters.addValue("fromDate", from);
        }
        if (to != null) {
            sql.append("\n  AND business_date <= :toDate");
            parameters.addValue("toDate", to);
        }
        if (sourceType != null) {
            sql.append("\n  AND source_reference_type = :sourceType");
            parameters.addValue("sourceType", sourceType);
        }
        if (sourceId != null) {
            sql.append("\n  AND source_reference_id = :sourceId");
            parameters.addValue("sourceId", sourceId);
        }
        sql.append("\nORDER BY txn_time DESC, id DESC");
        sql.append("\nLIMIT :limit OFFSET :offset");
        parameters.addValue("limit", size + 1);
        parameters.addValue("offset", page * size);
        List<InventoryTransactionResponse> items = jdbcTemplate.query(sql.toString(), parameters, transactionMapper());
        return toPageResponse(items, page, size);
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

    private RowMapper<StockBalanceResponse> stockBalanceMapper() {
        return (resultSet, rowNum) -> new StockBalanceResponse(
                resultSet.getLong("region_id"),
                resultSet.getLong("outlet_id"),
                resultSet.getLong("ingredient_id"),
                resultSet.getBigDecimal("qty_on_hand"),
                resultSet.getBigDecimal("qty_reserved"),
                resultSet.getBigDecimal("qty_available"),
                resultSet.getBigDecimal("unit_cost"),
                resultSet.getObject("last_count_date", LocalDate.class)
        );
    }

    private RowMapper<InventoryTransactionResponse> transactionMapper() {
        return (resultSet, rowNum) -> new InventoryTransactionResponse(
                resultSet.getLong("id"),
                resultSet.getLong("region_id"),
                resultSet.getLong("outlet_id"),
                resultSet.getLong("ingredient_id"),
                resultSet.getBigDecimal("qty_change"),
                resultSet.getObject("business_date", LocalDate.class),
                instant(resultSet, "txn_time"),
                resultSet.getString("txn_type"),
                resultSet.getBigDecimal("unit_cost"),
                resultSet.getString("source_reference_type"),
                resultSet.getString("source_reference_id"),
                resultSet.getObject("created_by_user_id", Long.class)
        );
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
