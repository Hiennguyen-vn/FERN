package com.fern.inventoryservice.service;

import com.fern.inventoryservice.dto.InventoryCommands.CreateStockCountSessionRequest;
import com.fern.inventoryservice.dto.InventoryCommands.StockCountLineInput;
import com.fern.inventoryservice.dto.InventoryCommands.UpdateStockCountLinesRequest;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountLineResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountSessionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountSessionSummaryResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.StockCountPostedEvent;
import com.fern.platform.contracts.StockCountPostedLine;
import com.fern.platform.observability.CorrelationId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockCountService {
    private static final int MAX_PAGE_SIZE = 200;

    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;
    private final InventoryOutboxService inventoryOutboxService;
    private final InventoryOrgClient inventoryOrgClient;
    private final Clock clock;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;

    public StockCountService(
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository,
            InventoryOutboxService inventoryOutboxService,
            InventoryOrgClient inventoryOrgClient,
            Clock clock,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver
    ) {
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
        this.inventoryOutboxService = inventoryOutboxService;
        this.inventoryOrgClient = inventoryOrgClient;
        this.clock = clock;
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
    }

    @Transactional
    public StockCountSessionResponse createStockCountSession(FernPrincipal principal, CreateStockCountSessionRequest request) {
        inventoryAuthorizer.requireOutletAccess(principal, request.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        InventoryOrgClient.OutletRoute outlet = inventoryOrgClient.requireOutlet(request.outletId());
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new BadRequestException("Region does not match the outlet route");
        }
        ensureOutletOperational(outlet, request.countDate());
        Long id = inventoryRepository.insertForId("""
                INSERT INTO inventory.stock_count_session (
                    region_id, outlet_id, count_date, status, note, counted_by_user_id, approved_by_user_id, created_at, updated_at,
                    created_by_user_id
                ) VALUES (
                    :regionId, :outletId, :countDate, :status, :note, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdByUserId
                )
                """, inventoryRepository.params(
                "regionId", outlet.regionId(),
                "outletId", request.outletId(),
                "countDate", request.countDate(),
                "status", StockCountSessionStatus.DRAFT.name(),
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        batchInsertCountLines(jdbcTemplate, id, request.ingredientIds());
        return getStockCountSession(id);
    }

    @Transactional
    public StockCountSessionResponse startStockCountSession(FernPrincipal principal, Long id) {
        StockCountSessionRecord record = requireStockCountSessionForUpdate(id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(record.regionId(), record.outletId());
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        ensureStatus(record.status(), StockCountSessionStatus.DRAFT.name(), "Only draft stock count sessions can be started");

        List<Long> lineIngredientIds = jdbcTemplate.queryForList("""
                SELECT ingredient_id
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId
                ORDER BY ingredient_id
                """, inventoryRepository.params("sessionId", id), Long.class);
        if (lineIngredientIds.isEmpty()) {
            lineIngredientIds = jdbcTemplate.queryForList("""
                    SELECT ingredient_id
                    FROM inventory.stock_balance
                    WHERE outlet_id = :outletId
                    ORDER BY ingredient_id
                    """, inventoryRepository.params("outletId", record.outletId()), Long.class);
            batchInsertCountLines(jdbcTemplate, id, lineIngredientIds);
        }
        for (Long ingredientId : lineIngredientIds) {
            BigDecimal systemQty = currentOnHand(record.outletId(), ingredientId);
            jdbcTemplate.update("""
                    UPDATE inventory.stock_count_line
                    SET system_qty = :systemQty, variance_qty = 0, updated_at = CURRENT_TIMESTAMP
                    WHERE stock_count_session_id = :sessionId AND ingredient_id = :ingredientId
                    """, inventoryRepository.params("systemQty", systemQty, "sessionId", id, "ingredientId", ingredientId));
        }
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_count_session
                SET status = :countingStatus, started_at = :startedAt, counted_by_user_id = :countedByUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :draftStatus
                """, inventoryRepository.params(
                "countingStatus", StockCountSessionStatus.COUNTING.name(),
                "startedAt", Instant.now(clock),
                "countedByUserId", principal.userId(),
                "draftStatus", StockCountSessionStatus.DRAFT.name(),
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only draft stock count sessions can be started");
        }
        return getStockCountSession(id);
    }

    @Transactional
    public StockCountSessionResponse updateStockCountLines(FernPrincipal principal, Long id, UpdateStockCountLinesRequest request) {
        StockCountSessionRecord record = requireStockCountSessionForUpdate(id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(record.regionId(), record.outletId());
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        ensureStatus(record.status(), StockCountSessionStatus.COUNTING.name(), "Only counting stock sessions can be updated");
        for (StockCountLineInput input : request.lines()) {
            BigDecimal systemQty = currentSystemQty(id, input.ingredientId()).orElseGet(() -> currentOnHand(record.outletId(), input.ingredientId()));
            jdbcTemplate.update("""
                    INSERT INTO inventory.stock_count_line (
                        stock_count_session_id, ingredient_id, system_qty, actual_qty, variance_qty, note, created_at, updated_at
                    ) VALUES (
                        :sessionId, :ingredientId, :systemQty, :actualQty, (:actualQty - :systemQty), :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (stock_count_session_id, ingredient_id) DO UPDATE
                    SET actual_qty = EXCLUDED.actual_qty,
                        variance_qty = EXCLUDED.actual_qty - inventory.stock_count_line.system_qty,
                        note = EXCLUDED.note,
                        updated_at = CURRENT_TIMESTAMP
                    """, inventoryRepository.params(
                    "sessionId", id,
                    "ingredientId", input.ingredientId(),
                    "systemQty", systemQty,
                    "actualQty", input.actualQty(),
                    "note", input.note()
            ));
        }
        return getStockCountSession(id);
    }

    @Transactional
    public StockCountSessionResponse postStockCountSession(FernPrincipal principal, Long id, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        StockCountSessionRecord record = requireStockCountSessionForUpdate(id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(record.regionId(), record.outletId());
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_POST);
        Long duplicateId = inventoryRepository.findIdempotentResourceId("stock-count-post", idempotencyKey);
        if (duplicateId != null) {
            if (!duplicateId.equals(id)) {
                throw new ConflictException("Idempotency-Key is already used for a different stock count session");
            }
            return getStockCountSession(duplicateId);
        }
        Long claimedId = inventoryRepository.claimIdempotentResource("stock-count-post", idempotencyKey, id);
        if (!id.equals(claimedId)) {
            throw new ConflictException("Idempotency-Key is already used for a different stock count session");
        }
        ensureStatus(record.status(), StockCountSessionStatus.COUNTING.name(), "Only counting stock sessions can be posted");
        List<StockCountLineRecord> lines = queryStockCountLines(id);
        if (lines.isEmpty()) {
            throw new BadRequestException("Stock count session has no lines");
        }
        for (StockCountLineRecord line : lines) {
            if (line.actualQty() == null) {
                throw new BadRequestException("All stock count lines must have actual quantity before posting");
            }
        }

        for (StockCountLineRecord line : lines) {
            BigDecimal variance = line.actualQty().subtract(line.systemQty());
            jdbcTemplate.update("""
                    UPDATE inventory.stock_count_line
                    SET variance_qty = :varianceQty, updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, inventoryRepository.params("varianceQty", variance, "id", line.id()));
            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                updateLastCountDate(record.countDate(), record.outletId(), line.ingredientId());
                continue;
            }
            InventoryRepository.StockBalanceSnapshot balance = inventoryRepository.lockStockBalance(
                    record.regionId(),
                    record.outletId(),
                    line.ingredientId()
            );
            ensureNonNegative(balance.qtyOnHand(), variance, line.ingredientId());
            String txnType = variance.signum() > 0
                    ? InventoryTxnType.STOCK_ADJUSTMENT_IN.name()
                    : InventoryTxnType.STOCK_ADJUSTMENT_OUT.name();
            BigDecimal unitCost = balance.unitCost();
            inventoryRepository.appendTransaction(
                    record.regionId(),
                    record.outletId(),
                    line.ingredientId(),
                    variance,
                    record.countDate(),
                    txnType,
                    unitCost,
                    "STOCK_COUNT_SESSION",
                    id.toString(),
                    principal.userId()
            );
            inventoryRepository.applyBalanceDelta(record.regionId(), record.outletId(), line.ingredientId(), variance, unitCost, true);
            updateLastCountDate(record.countDate(), record.outletId(), line.ingredientId());
        }

        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_count_session
                SET status = :postedStatus,
                    posted_at = :postedAt,
                    posted_by_user_id = :postedByUserId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :countingStatus
                """, inventoryRepository.params(
                "postedStatus", StockCountSessionStatus.POSTED.name(),
                "postedAt", Instant.now(clock),
                "postedByUserId", principal.userId(),
                "countingStatus", StockCountSessionStatus.COUNTING.name(),
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only counting stock sessions can be posted");
        }
        List<StockCountLineRecord> postedLines = queryStockCountLines(id);
        StockCountSessionResponse response = getStockCountSession(id);
        enqueueStockCountPosted(response, record, principal, postedLines);
        return response;
    }

    @Transactional
    public StockCountSessionResponse cancelStockCountSession(FernPrincipal principal, Long id) {
        StockCountSessionRecord record = requireStockCountSessionForUpdate(id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(record.regionId(), record.outletId());
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        if (!List.of(StockCountSessionStatus.DRAFT.name(), StockCountSessionStatus.COUNTING.name()).contains(record.status())) {
            throw new ConflictException("Only draft or counting stock sessions can be cancelled");
        }
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_count_session
                SET status = :cancelledStatus, cancelled_at = :cancelledAt, cancelled_by_user_id = :cancelledByUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :status
                """, inventoryRepository.params(
                "cancelledStatus", StockCountSessionStatus.CANCELLED.name(),
                "cancelledAt", Instant.now(clock),
                "cancelledByUserId", principal.userId(),
                "id", id,
                "status", record.status()
        ));
        if (updated != 1) {
            throw new ConflictException("Only draft or counting stock sessions can be cancelled");
        }
        return getStockCountSession(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockCountSessionSummaryResponse> listStockCountSessions(
            FernPrincipal principal,
            Long outletId,
            String status,
            int page,
            int size
    ) {
        validatePage(page, size);
        validateStatusFilter(status);
        inventoryAuthorizer.requireOutletAccess(principal, outletId, PermissionCodes.INVENTORY_BALANCE_READ);
        InventoryOrgClient.OutletRoute outlet = inventoryOrgClient.requireOutlet(outletId);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), outletId);
        StringBuilder sql = new StringBuilder("""
                SELECT id, status, region_id, outlet_id, count_date, note, started_at, posted_at
                FROM inventory.stock_count_session
                WHERE outlet_id = :outletId
                """);
        MapSqlParameterSource parameters = inventoryRepository.params("outletId", outletId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            parameters.addValue("status", status.trim().toUpperCase());
        }
        sql.append(" ORDER BY id DESC LIMIT :limit OFFSET :offset");
        parameters.addValue("limit", size + 1);
        parameters.addValue("offset", page * size);
        List<StockCountSessionSummaryResponse> items = jdbcTemplate.query(
                sql.toString(),
                parameters,
                (rs, rowNum) -> new StockCountSessionSummaryResponse(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getObject("count_date", LocalDate.class),
                        rs.getString("note"),
                        instant(rs, "started_at"),
                        instant(rs, "posted_at")
                )
        );
        return toPageResponse(items, page, size);
    }

    @Transactional(readOnly = true)
    public StockCountSessionResponse readStockCountSession(FernPrincipal principal, Long id) {
        Long outletId = jdbcTemplate(null, null).query("""
                SELECT outlet_id
                FROM inventory.stock_count_session
                WHERE id = :id
                """, inventoryRepository.params("id", id), rs -> rs.next() ? rs.getLong("outlet_id") : null);
        if (outletId == null) {
            throw new ResourceNotFoundException("Stock count session not found");
        }
        inventoryAuthorizer.requireOutletAccess(principal, outletId, PermissionCodes.INVENTORY_BALANCE_READ);
        return getStockCountSession(id);
    }

    public StockCountSessionResponse getStockCountSession(Long id) {
        SessionProjection session = jdbcTemplate(null, null).query("""
                SELECT id, status, region_id, outlet_id, count_date, note, started_at, posted_at
                FROM inventory.stock_count_session
                WHERE id = :id
                """, inventoryRepository.params("id", id), rs -> rs.next()
                ? new SessionProjection(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getObject("count_date", LocalDate.class),
                        rs.getString("note"),
                        instant(rs, "started_at"),
                        instant(rs, "posted_at")
                )
                : null);
        if (session == null) {
            throw new ResourceNotFoundException("Stock count session not found");
        }
        List<StockCountLineResponse> lines = jdbcTemplate(null, null).query("""
                SELECT ingredient_id, system_qty, actual_qty, variance_qty, note
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId
                ORDER BY ingredient_id
                """, inventoryRepository.params("sessionId", id), (rs, rowNum) -> new StockCountLineResponse(
                rs.getLong("ingredient_id"),
                rs.getBigDecimal("system_qty"),
                rs.getBigDecimal("actual_qty"),
                rs.getBigDecimal("variance_qty"),
                rs.getString("note")
        ));
        return new StockCountSessionResponse(
                session.id(),
                session.status(),
                session.regionId(),
                session.outletId(),
                session.countDate(),
                session.note(),
                session.startedAt(),
                session.postedAt(),
                lines
        );
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private void ensureStatus(String actualStatus, String expectedStatus, String message) {
        if (!expectedStatus.equals(actualStatus)) {
            throw new ConflictException(message);
        }
    }

    private void ensureNonNegative(BigDecimal qtyOnHand, BigDecimal delta, Long ingredientId) {
        BigDecimal effectiveProjected = (qtyOnHand == null ? BigDecimal.ZERO : qtyOnHand).add(delta);
        if (effectiveProjected.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConflictException("Inventory would become negative for ingredient " + ingredientId);
        }
    }

    private void ensureOutletOperational(InventoryOrgClient.OutletRoute outlet, LocalDate businessDate) {
        if (!outlet.isActive() || outlet.isClosedOn(businessDate)) {
            throw new ConflictException("Outlet is inactive or closed for inventory workflows");
        }
    }

    private List<StockCountLineRecord> queryStockCountLines(Long sessionId) {
        return jdbcTemplate(null, null).query("""
                SELECT id, ingredient_id, system_qty, actual_qty, variance_qty, note
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId
                ORDER BY ingredient_id
                """, inventoryRepository.params("sessionId", sessionId), (rs, rowNum) -> new StockCountLineRecord(
                rs.getLong("id"),
                rs.getLong("ingredient_id"),
                rs.getBigDecimal("system_qty"),
                rs.getBigDecimal("actual_qty"),
                rs.getBigDecimal("variance_qty"),
                rs.getString("note")
        ));
    }

    private Optional<BigDecimal> currentSystemQty(Long sessionId, Long ingredientId) {
        return jdbcTemplate(null, null).query("""
                SELECT system_qty
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId AND ingredient_id = :ingredientId
                """, inventoryRepository.params("sessionId", sessionId, "ingredientId", ingredientId), rs -> rs.next()
                ? Optional.ofNullable(rs.getBigDecimal("system_qty"))
                : Optional.empty());
    }

    private BigDecimal currentOnHand(Long outletId, Long ingredientId) {
        BigDecimal result = jdbcTemplate(null, null).query("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, inventoryRepository.params("outletId", outletId, "ingredientId", ingredientId), rs -> rs.next() ? rs.getBigDecimal("qty_on_hand") : null);
        return result == null ? BigDecimal.ZERO : result;
    }

    private void updateLastCountDate(LocalDate countDate, Long outletId, Long ingredientId) {
        jdbcTemplate(null, null).update("""
                UPDATE inventory.stock_balance
                SET last_count_date = :countDate, updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, inventoryRepository.params("countDate", countDate, "outletId", outletId, "ingredientId", ingredientId));
    }

    private StockCountSessionRecord requireStockCountSessionForUpdate(Long id) {
        StockCountSessionRecord record = jdbcTemplate(null, null).query("""
                SELECT id, status, region_id, outlet_id, count_date
                FROM inventory.stock_count_session
                WHERE id = :id
                FOR UPDATE
                """, inventoryRepository.params("id", id), rs -> rs.next()
                ? new StockCountSessionRecord(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getObject("count_date", LocalDate.class)
                )
                : null);
        if (record == null) {
            throw new ResourceNotFoundException("Stock count session not found");
        }
        return record;
    }

    private void enqueueStockCountPosted(
            StockCountSessionResponse response,
            StockCountSessionRecord record,
            FernPrincipal principal,
            List<StockCountLineRecord> lines
    ) {
        StockCountPostedEvent event = new StockCountPostedEvent(
                UUID.randomUUID().toString(),
                "inventory.stock_count.posted",
                response.postedAt(),
                "inventory-service",
                currentCorrelationId(),
                stockCountPostedIdempotencyKey(record.id()),
                record.id(),
                record.regionId(),
                record.outletId(),
                record.countDate(),
                response.postedAt(),
                principal.userId(),
                lines.stream()
                        .map(line -> new StockCountPostedLine(
                                line.ingredientId(),
                                line.systemQty(),
                                line.actualQty(),
                                line.varianceQty(),
                                inventoryRepository.currentUnitCost(record.outletId(), line.ingredientId())
                        ))
                        .toList()
        );
        inventoryOutboxService.enqueueOutbox("STOCK_COUNT_SESSION", record.id().toString(), event.eventType(), record.outletId().toString(), event);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String currentCorrelationId() {
        return MDC.get(CorrelationId.MDC_KEY);
    }

    private String stockCountPostedIdempotencyKey(Long stockCountSessionId) {
        return "inventory.stock_count.posted:session:" + stockCountSessionId;
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page cannot be negative");
        }
        if (size <= 0) {
            throw new BadRequestException("Page size must be positive");
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

    private void validateStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        try {
            StockCountSessionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid stock count session status filter");
        }
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
    }

    private record StockCountSessionRecord(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            LocalDate countDate
    ) {
    }

    private record StockCountLineRecord(
            Long id,
            Long ingredientId,
            BigDecimal systemQty,
            BigDecimal actualQty,
            BigDecimal varianceQty,
            String note
    ) {
    }

    private record SessionProjection(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            LocalDate countDate,
            String note,
            Instant startedAt,
            Instant postedAt
    ) {
    }

    private void batchInsertCountLines(NamedParameterJdbcTemplate jdbcTemplate, Long sessionId, List<Long> ingredientIds) {
        if (ingredientIds.isEmpty()) {
            return;
        }
        org.springframework.jdbc.core.namedparam.SqlParameterSource[] batchParams = ingredientIds.stream()
                .map(ingredientId -> inventoryRepository.params("sessionId", sessionId, "ingredientId", ingredientId))
                .toArray(org.springframework.jdbc.core.namedparam.SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO inventory.stock_count_line (
                    stock_count_session_id, ingredient_id, system_qty, actual_qty, variance_qty, note, created_at, updated_at
                ) VALUES (
                    :sessionId, :ingredientId, 0, NULL, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                ON CONFLICT (stock_count_session_id, ingredient_id) DO NOTHING
                """, batchParams);
    }
}
