package com.fern.posservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.posservice.dto.PosCommands.CreateTableRequest;
import com.fern.posservice.dto.PosCommands.UpdateTableRequest;
import com.fern.posservice.dto.PosResponses.DiningTableResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PosDineInService {
    private final PosAuthorizer posAuthorizer;
    private final PosOrgClient posOrgClient;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;

    public PosDineInService(
            PosAuthorizer posAuthorizer,
            PosOrgClient posOrgClient,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver
    ) {
        this.posAuthorizer = posAuthorizer;
        this.posOrgClient = posOrgClient;
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
    }

    public DiningTableResponse createTable(FernPrincipal principal, CreateTableRequest request) {
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(request.outletId());
        posAuthorizer.requireRoutePermission(principal, outlet.regionId(), request.outletId(), PermissionCodes.POS_TABLE_WRITE);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), request.outletId());
        String tableCode = request.tableCode() != null ? request.tableCode()
                : "TBL-" + System.currentTimeMillis() % 100000;
        Long id = PosSql.insertForId(jdbcTemplate, """
                INSERT INTO pos.dining_table (
                    outlet_id, table_name, table_code, capacity, zone, status, note, created_at, updated_at
                ) VALUES (
                    :outletId, :tableName, :tableCode, :capacity, :zone, :status, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PosSql.params(
                "outletId", request.outletId(),
                "tableName", request.tableName(),
                "tableCode", tableCode,
                "capacity", request.capacity(),
                "zone", request.zone(),
                "status", DiningTableStatus.AVAILABLE.name(),
                "note", request.note()
        ));
        return getTable(principal, id, outlet.regionId(), request.outletId());
    }

    public DiningTableResponse updateTable(FernPrincipal principal, Long id, UpdateTableRequest request) {
        DiningTableRecord record = requireTableById(id);
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(record.outletId());
        posAuthorizer.requireRoutePermission(principal, outlet.regionId(), record.outletId(), PermissionCodes.POS_TABLE_WRITE);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), record.outletId());
        jdbcTemplate.update("""
                UPDATE pos.dining_table
                SET table_name = COALESCE(:tableName, table_name),
                    table_code = COALESCE(:tableCode, table_code),
                    capacity = COALESCE(:capacity, capacity),
                    zone = COALESCE(:zone, zone),
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, PosSql.params(
                "tableName", request.tableName(),
                "tableCode", request.tableCode(),
                "capacity", request.capacity(),
                "zone", request.zone(),
                "note", request.note(),
                "id", id
        ));
        return getTable(principal, id, outlet.regionId(), record.outletId());
    }

    public List<DiningTableResponse> listTables(FernPrincipal principal, Long outletId) {
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(outletId);
        posAuthorizer.requireRoutePermission(principal, outlet.regionId(), outletId, PermissionCodes.POS_TABLE_READ);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), outletId);
        return jdbcTemplate.query("""
                SELECT id, outlet_id, table_name, table_code, capacity, zone, status, current_order_id, note, created_at, updated_at
                FROM pos.dining_table
                WHERE outlet_id = :outletId
                ORDER BY zone NULLS LAST, table_name
                """, PosSql.params("outletId", outletId), (rs, rowNum) -> new DiningTableResponse(
                rs.getLong("id"),
                rs.getLong("outlet_id"),
                rs.getString("table_name"),
                rs.getString("table_code"),
                rs.getObject("capacity", Integer.class),
                rs.getString("zone"),
                rs.getString("status"),
                rs.getObject("current_order_id", Long.class),
                rs.getString("note"),
                PosSql.instant(rs, "created_at"),
                PosSql.instant(rs, "updated_at")
        ));
    }

    public DiningTableResponse getTableById(FernPrincipal principal, Long id) {
        DiningTableRecord record = requireTableById(id);
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(record.outletId());
        posAuthorizer.requireRoutePermission(principal, outlet.regionId(), record.outletId(), PermissionCodes.POS_TABLE_READ);
        return getTable(principal, id, outlet.regionId(), record.outletId());
    }

    /**
     * Assigns a table to an order. Sets the table status to OCCUPIED and links the current_order_id.
     * Called from PosOrderService during order creation when tableId is specified.
     */
    public void assignTableToOrder(Long tableId, Long orderId, Long regionId, Long outletId) {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(regionId, outletId);
        int updated = jdbcTemplate.update("""
                UPDATE pos.dining_table
                SET status = :newStatus,
                    current_order_id = :orderId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status IN (:availableStatus, :reservedStatus)
                """, PosSql.params(
                "newStatus", DiningTableStatus.OCCUPIED.name(),
                "orderId", orderId,
                "id", tableId,
                "availableStatus", DiningTableStatus.AVAILABLE.name(),
                "reservedStatus", DiningTableStatus.RESERVED.name()
        ));
        if (updated != 1) {
            throw new ConflictException("Table is not available for assignment");
        }
    }

    /**
     * Releases a table after order completion or cancellation.
     * Sets table to CLEANING status (frontend can transition to AVAILABLE).
     */
    public void releaseTable(Long tableId, Long regionId, Long outletId) {
        if (tableId == null) return;
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(regionId, outletId);
        jdbcTemplate.update("""
                UPDATE pos.dining_table
                SET status = :status,
                    current_order_id = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :occupiedStatus
                """, PosSql.params(
                "status", DiningTableStatus.AVAILABLE.name(),
                "id", tableId,
                "occupiedStatus", DiningTableStatus.OCCUPIED.name()
        ));
    }

    /**
     * Manually update table status (e.g., CLEANING → AVAILABLE, AVAILABLE → RESERVED).
     */
    public DiningTableResponse updateTableStatus(FernPrincipal principal, Long id, String newStatus) {
        DiningTableRecord record = requireTableById(id);
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(record.outletId());
        posAuthorizer.requireRoutePermission(principal, outlet.regionId(), record.outletId(), PermissionCodes.POS_TABLE_MANAGE);
        DiningTableStatus targetStatus;
        try {
            targetStatus = DiningTableStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid table status: " + newStatus);
        }
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), record.outletId());
        int updated = jdbcTemplate.update("""
                UPDATE pos.dining_table
                SET status = :newStatus,
                    current_order_id = CASE WHEN :newStatus IN ('AVAILABLE', 'RESERVED', 'CLEANING') THEN NULL ELSE current_order_id END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, PosSql.params(
                "newStatus", targetStatus.name(),
                "id", id
        ));
        if (updated != 1) {
            throw new ResourceNotFoundException("Table not found");
        }
        return getTable(principal, id, outlet.regionId(), record.outletId());
    }

    public String resolveTableName(NamedParameterJdbcTemplate jdbcTemplate, Long tableId) {
        if (tableId == null) return null;
        return jdbcTemplate.query("""
                SELECT table_name FROM pos.dining_table WHERE id = :id
                """, PosSql.params("id", tableId), rs -> rs.next() ? rs.getString("table_name") : null);
    }

    private DiningTableResponse getTable(FernPrincipal principal, Long id, Long regionId, Long outletId) {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(regionId, outletId);
        return jdbcTemplate.query("""
                SELECT id, outlet_id, table_name, table_code, capacity, zone, status, current_order_id, note, created_at, updated_at
                FROM pos.dining_table
                WHERE id = :id
                """, PosSql.params("id", id), rs -> {
            if (!rs.next()) throw new ResourceNotFoundException("Dining table not found");
            return new DiningTableResponse(
                    rs.getLong("id"),
                    rs.getLong("outlet_id"),
                    rs.getString("table_name"),
                    rs.getString("table_code"),
                    rs.getObject("capacity", Integer.class),
                    rs.getString("zone"),
                    rs.getString("status"),
                    rs.getObject("current_order_id", Long.class),
                    rs.getString("note"),
                    PosSql.instant(rs, "created_at"),
                    PosSql.instant(rs, "updated_at")
            );
        });
    }

    private DiningTableRecord requireTableById(Long id) {
        // Use root template to find the table across all shards
        NamedParameterJdbcTemplate rootJdbc = operationalShardRegistry.get(
                shardResolver.resolve(RouteKey.of(0L, 0L))).jdbc();
        DiningTableRecord record = rootJdbc.query("""
                SELECT id, outlet_id FROM pos.dining_table WHERE id = :id
                """, PosSql.params("id", id), rs -> rs.next()
                    ? new DiningTableRecord(rs.getLong("id"), rs.getLong("outlet_id"))
                    : null);
        if (record == null) {
            throw new ResourceNotFoundException("Dining table not found");
        }
        return record;
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
    }

    private record DiningTableRecord(Long id, Long outletId) {}

    enum DiningTableStatus {
        AVAILABLE, OCCUPIED, RESERVED, CLEANING
    }
}
