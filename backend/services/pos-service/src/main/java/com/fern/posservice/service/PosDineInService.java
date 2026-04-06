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
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PosDineInService {
    private static final Logger log = LoggerFactory.getLogger(PosDineInService.class);
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
                : "TBL-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
                LIMIT 500
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
                  AND outlet_id = :outletId
                  AND status IN (:availableStatus, :reservedStatus)
                """, PosSql.params(
                "newStatus", DiningTableStatus.OCCUPIED.name(),
                "orderId", orderId,
                "id", tableId,
                "outletId", outletId,
                "availableStatus", DiningTableStatus.AVAILABLE.name(),
                "reservedStatus", DiningTableStatus.RESERVED.name()
        ));
        if (updated != 1) {
            throw new ConflictException("Table does not belong to the outlet or is not available for assignment");
        }
    }

    /**
     * Releases a table after order completion or cancellation.
     * Sets table to CLEANING status so staff can clear / reset the table.
     * The frontend or manual status update transitions CLEANING → AVAILABLE.
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
                  AND outlet_id = :outletId
                  AND status = :occupiedStatus
                """, PosSql.params(
                "status", DiningTableStatus.CLEANING.name(),
                "id", tableId,
                "outletId", outletId,
                "occupiedStatus", DiningTableStatus.OCCUPIED.name()
        ));
    }

    /**
     * Valid manual table status transitions.
     *
     * <p>OCCUPIED → CLEANING is the only exit from OCCUPIED (automatically via order completion,
     * or manually by staff). Direct OCCUPIED → AVAILABLE is forbidden to ensure the cleaning step.
     *
     * <p>Note: AVAILABLE → OCCUPIED is NOT allowed manually — tables become OCCUPIED only
     * through {@link #assignTableToOrder}, which links the table to an active order.
     */
    private static final Map<DiningTableStatus, Set<DiningTableStatus>> VALID_TRANSITIONS = Map.of(
            DiningTableStatus.AVAILABLE, Set.of(DiningTableStatus.RESERVED, DiningTableStatus.CLEANING),
            DiningTableStatus.RESERVED, Set.of(DiningTableStatus.AVAILABLE),
            DiningTableStatus.OCCUPIED, Set.of(DiningTableStatus.CLEANING),
            DiningTableStatus.CLEANING, Set.of(DiningTableStatus.AVAILABLE)
    );

    /**
     * Manually update table status (e.g., CLEANING → AVAILABLE, AVAILABLE → RESERVED).
     * Enforces a state machine — only valid transitions are allowed.
     * Logs an audit trail of the status transition for operational traceability.
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
        // Capture old status for audit trail and transition validation
        String oldStatus = jdbcTemplate.query("""
                SELECT status FROM pos.dining_table WHERE id = :id
                """, PosSql.params("id", id), rs -> rs.next() ? rs.getString("status") : null);
        if (oldStatus == null) {
            throw new ResourceNotFoundException("Table not found");
        }
        // P1-01 FIX: Enforce table status state machine
        DiningTableStatus currentStatus;
        try {
            currentStatus = DiningTableStatus.valueOf(oldStatus);
        } catch (IllegalArgumentException e) {
            throw new ConflictException("Table has an unknown status: " + oldStatus);
        }
        if (currentStatus == targetStatus) {
            return getTable(principal, id, outlet.regionId(), record.outletId());
        }
        Set<DiningTableStatus> allowedTargets = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new ConflictException(
                    "Cannot transition table from " + currentStatus + " to " + targetStatus
                    + ". Allowed transitions: " + allowedTargets
            );
        }
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
        // M-04: Audit trail for table status changes
        log.info("TABLE_STATUS_CHANGE tableId={} outletId={} oldStatus={} newStatus={} changedBy={}",
                id, record.outletId(), oldStatus, targetStatus.name(), principal.userId());
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

    /**
     * Resolves a dining table by its ID.
     * Uses the outlet's known region to route to the correct shard instead of
     * blindly querying the root shard (which may not contain the table).
     */
    private DiningTableRecord requireTableById(Long id) {
        // First, try to find the table in any shard using a lightweight lookup.
        // We scan all registered shards to locate the table, then use the proper
        // shard-aware template for subsequent operations.
        for (var shardEntry : operationalShardRegistry.allShards()) {
            DiningTableRecord record = shardEntry.jdbc().query("""
                    SELECT id, outlet_id FROM pos.dining_table WHERE id = :id
                    """, PosSql.params("id", id), rs -> rs.next()
                        ? new DiningTableRecord(rs.getLong("id"), rs.getLong("outlet_id"))
                        : null);
            if (record != null) {
                return record;
            }
        }
        throw new ResourceNotFoundException("Dining table not found");
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
    }

    private record DiningTableRecord(Long id, Long outletId) {}

    enum DiningTableStatus {
        AVAILABLE, OCCUPIED, RESERVED, CLEANING
    }
}
