package com.fern.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.inventoryservice.dto.InventoryCommands.CreateStockAdjustmentRequest;
import com.fern.inventoryservice.dto.InventoryCommands.CreateStockCountSessionRequest;
import com.fern.inventoryservice.dto.InventoryCommands.CreateWasteRecordRequest;
import com.fern.inventoryservice.dto.InventoryCommands.StockCountLineInput;
import com.fern.inventoryservice.dto.InventoryCommands.UpdateStockCountLinesRequest;
import com.fern.inventoryservice.dto.InventoryResponses.InventoryTransactionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockAdjustmentResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockBalanceResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountLineResponse;
import com.fern.inventoryservice.dto.InventoryResponses.StockCountSessionResponse;
import com.fern.inventoryservice.dto.InventoryResponses.WasteRecordResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryAuthorizer inventoryAuthorizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InventoryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryAuthorizer inventoryAuthorizer,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StockBalanceResponse> listStockBalances(FernPrincipal principal, Long outletId, Long ingredientId) {
        inventoryAuthorizer.requireOutletAccess(principal, outletId, PermissionCodes.INVENTORY_BALANCE_READ);
        String sql = """
                SELECT region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, last_count_date
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId
                  AND (:ingredientId IS NULL OR ingredient_id = :ingredientId)
                ORDER BY ingredient_id
                """;
        return jdbcTemplate.query(sql, params("outletId", outletId, "ingredientId", ingredientId), stockBalanceMapper());
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> listInventoryTransactions(
            FernPrincipal principal,
            Long outletId,
            Long ingredientId,
            String txnType,
            LocalDate from,
            LocalDate to,
            String sourceType,
            String sourceId
    ) {
        inventoryAuthorizer.requireOutletAccess(principal, outletId, PermissionCodes.INVENTORY_LEDGER_READ);
        String sql = """
                SELECT id, region_id, outlet_id, ingredient_id, qty_change, business_date, txn_time, txn_type,
                       unit_cost, source_reference_type, source_reference_id, created_by_user_id
                FROM inventory.inventory_transaction
                WHERE outlet_id = :outletId
                  AND (:ingredientId IS NULL OR ingredient_id = :ingredientId)
                  AND (:txnType IS NULL OR txn_type = :txnType)
                  AND (:fromDate IS NULL OR business_date >= :fromDate)
                  AND (:toDate IS NULL OR business_date <= :toDate)
                  AND (:sourceType IS NULL OR source_reference_type = :sourceType)
                  AND (:sourceId IS NULL OR source_reference_id = :sourceId)
                ORDER BY txn_time DESC, id DESC
                """;
        return jdbcTemplate.query(sql, params(
                "outletId", outletId,
                "ingredientId", ingredientId,
                "txnType", txnType,
                "fromDate", from,
                "toDate", to,
                "sourceType", sourceType,
                "sourceId", sourceId
        ), transactionMapper());
    }

    @Transactional
    public StockAdjustmentResponse createStockAdjustment(FernPrincipal principal, CreateStockAdjustmentRequest request) {
        inventoryAuthorizer.requireOutletAccess(principal, request.outletId(), PermissionCodes.INVENTORY_ADJUSTMENT_WRITE);
        validateDirection(request.adjustmentDirection());
        Long id = insertForId("""
                INSERT INTO inventory.stock_adjustment (
                    inventory_transaction_id, adjustment_direction, reason, approved_by_user_id, created_at, updated_at,
                    region_id, outlet_id, ingredient_id, qty, business_date, status, note, created_by_user_id
                ) VALUES (
                    NULL, :direction, :reason, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    :regionId, :outletId, :ingredientId, :qty, :businessDate, 'DRAFT', :note, :createdByUserId
                )
                """, params(
                "direction", request.adjustmentDirection(),
                "reason", request.reason(),
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "ingredientId", request.ingredientId(),
                "qty", request.qty(),
                "businessDate", request.businessDate(),
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        return getStockAdjustment(id);
    }

    @Transactional
    public StockAdjustmentResponse postStockAdjustment(FernPrincipal principal, Long id, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        StockAdjustmentRecord record = requireStockAdjustmentRecord(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_ADJUSTMENT_WRITE);
        Long duplicateId = findIdempotentResourceId("stock-adjustment-post", idempotencyKey);
        if (duplicateId != null) {
            return getStockAdjustment(duplicateId);
        }
        ensureStatus(record.status(), "DRAFT", "Only draft stock adjustments can be posted");
        BigDecimal signedQty = "IN".equals(record.adjustmentDirection()) ? record.qty() : record.qty().negate();
        ensureNonNegative(record.outletId(), record.ingredientId(), signedQty);
        Long transactionId = appendTransaction(
                record.regionId(),
                record.outletId(),
                record.ingredientId(),
                signedQty,
                record.businessDate(),
                "IN".equals(record.adjustmentDirection()) ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT",
                null,
                "STOCK_ADJUSTMENT",
                id.toString(),
                principal.userId()
        );
        applyBalanceDelta(record.regionId(), record.outletId(), record.ingredientId(), signedQty, null, false);
        jdbcTemplate.update("""
                UPDATE inventory.stock_adjustment
                SET inventory_transaction_id = :transactionId,
                    status = 'POSTED',
                    posted_at = :postedAt,
                    posted_by_user_id = :postedByUserId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "transactionId", transactionId,
                "postedAt", Instant.now(clock),
                "postedByUserId", principal.userId(),
                "id", id
        ));
        recordIdempotentResource("stock-adjustment-post", idempotencyKey, id);
        return getStockAdjustment(id);
    }

    @Transactional
    public StockAdjustmentResponse cancelStockAdjustment(FernPrincipal principal, Long id) {
        StockAdjustmentRecord record = requireStockAdjustmentRecord(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_ADJUSTMENT_WRITE);
        if (!"DRAFT".equals(record.status())) {
            throw new ConflictException("Only draft stock adjustments can be cancelled");
        }
        jdbcTemplate.update("""
                UPDATE inventory.stock_adjustment
                SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("id", id));
        return getStockAdjustment(id);
    }

    @Transactional
    public WasteRecordResponse createWasteRecord(FernPrincipal principal, CreateWasteRecordRequest request) {
        inventoryAuthorizer.requireOutletAccess(principal, request.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        Long id = insertForId("""
                INSERT INTO inventory.waste_record (
                    inventory_transaction_id, status, reason, submitted_by_user_id, approved_by_user_id, created_at, updated_at,
                    region_id, outlet_id, ingredient_id, qty, business_date, note, created_by_user_id
                ) VALUES (
                    NULL, 'DRAFT', :reason, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    :regionId, :outletId, :ingredientId, :qty, :businessDate, :note, :createdByUserId
                )
                """, params(
                "reason", request.reason(),
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "ingredientId", request.ingredientId(),
                "qty", request.qty(),
                "businessDate", request.businessDate(),
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        return getWasteRecord(id);
    }

    @Transactional
    public WasteRecordResponse postWasteRecord(FernPrincipal principal, Long id, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        WasteRecord record = requireWasteRecordRecord(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        Long duplicateId = findIdempotentResourceId("waste-record-post", idempotencyKey);
        if (duplicateId != null) {
            return getWasteRecord(duplicateId);
        }
        ensureStatus(record.status(), "DRAFT", "Only draft waste records can be posted");
        BigDecimal signedQty = record.qty().negate();
        ensureNonNegative(record.outletId(), record.ingredientId(), signedQty);
        Long transactionId = appendTransaction(
                record.regionId(),
                record.outletId(),
                record.ingredientId(),
                signedQty,
                record.businessDate(),
                "WASTE_OUT",
                null,
                "WASTE_RECORD",
                id.toString(),
                principal.userId()
        );
        applyBalanceDelta(record.regionId(), record.outletId(), record.ingredientId(), signedQty, null, false);
        jdbcTemplate.update("""
                UPDATE inventory.waste_record
                SET inventory_transaction_id = :transactionId,
                    status = 'POSTED',
                    posted_at = :postedAt,
                    posted_by_user_id = :postedByUserId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "transactionId", transactionId,
                "postedAt", Instant.now(clock),
                "postedByUserId", principal.userId(),
                "id", id
        ));
        recordIdempotentResource("waste-record-post", idempotencyKey, id);
        return getWasteRecord(id);
    }

    @Transactional
    public WasteRecordResponse cancelWasteRecord(FernPrincipal principal, Long id) {
        WasteRecord record = requireWasteRecordRecord(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        if (!"DRAFT".equals(record.status())) {
            throw new ConflictException("Only draft waste records can be cancelled");
        }
        jdbcTemplate.update("""
                UPDATE inventory.waste_record
                SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("id", id));
        return getWasteRecord(id);
    }

    @Transactional
    public StockCountSessionResponse createStockCountSession(FernPrincipal principal, CreateStockCountSessionRequest request) {
        inventoryAuthorizer.requireOutletAccess(principal, request.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        Long id = insertForId("""
                INSERT INTO inventory.stock_count_session (
                    region_id, outlet_id, count_date, status, note, counted_by_user_id, approved_by_user_id, created_at, updated_at,
                    created_by_user_id
                ) VALUES (
                    :regionId, :outletId, :countDate, 'DRAFT', :note, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :createdByUserId
                )
                """, params(
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "countDate", request.countDate(),
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        for (Long ingredientId : request.ingredientIds()) {
            jdbcTemplate.update("""
                    INSERT INTO inventory.stock_count_line (
                        stock_count_session_id, ingredient_id, system_qty, actual_qty, variance_qty, note, created_at, updated_at
                    ) VALUES (
                        :sessionId, :ingredientId, 0, NULL, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (stock_count_session_id, ingredient_id) DO NOTHING
                    """, params("sessionId", id, "ingredientId", ingredientId));
        }
        return getStockCountSession(id);
    }

    @Transactional
    public StockCountSessionResponse startStockCountSession(FernPrincipal principal, Long id) {
        StockCountSessionRecord record = requireStockCountSession(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        ensureStatus(record.status(), "DRAFT", "Only draft stock count sessions can be started");

        List<Long> lineIngredientIds = jdbcTemplate.queryForList("""
                SELECT ingredient_id
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId
                ORDER BY ingredient_id
                """, params("sessionId", id), Long.class);
        if (lineIngredientIds.isEmpty()) {
            lineIngredientIds = jdbcTemplate.queryForList("""
                    SELECT ingredient_id
                    FROM inventory.stock_balance
                    WHERE outlet_id = :outletId
                    ORDER BY ingredient_id
                    """, params("outletId", record.outletId()), Long.class);
            for (Long ingredientId : lineIngredientIds) {
                jdbcTemplate.update("""
                        INSERT INTO inventory.stock_count_line (
                            stock_count_session_id, ingredient_id, system_qty, actual_qty, variance_qty, note, created_at, updated_at
                        ) VALUES (
                            :sessionId, :ingredientId, 0, NULL, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (stock_count_session_id, ingredient_id) DO NOTHING
                        """, params("sessionId", id, "ingredientId", ingredientId));
            }
        }
        for (Long ingredientId : lineIngredientIds) {
            BigDecimal systemQty = currentOnHand(record.outletId(), ingredientId);
            jdbcTemplate.update("""
                    UPDATE inventory.stock_count_line
                    SET system_qty = :systemQty, variance_qty = 0, updated_at = CURRENT_TIMESTAMP
                    WHERE stock_count_session_id = :sessionId AND ingredient_id = :ingredientId
                    """, params("systemQty", systemQty, "sessionId", id, "ingredientId", ingredientId));
        }
        jdbcTemplate.update("""
                UPDATE inventory.stock_count_session
                SET status = 'COUNTING', started_at = :startedAt, counted_by_user_id = :countedByUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "startedAt", Instant.now(clock),
                "countedByUserId", principal.userId(),
                "id", id
        ));
        return getStockCountSession(id);
    }

    @Transactional
    public StockCountSessionResponse updateStockCountLines(FernPrincipal principal, Long id, UpdateStockCountLinesRequest request) {
        StockCountSessionRecord record = requireStockCountSession(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        ensureStatus(record.status(), "COUNTING", "Only counting stock sessions can be updated");
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
                    """, params(
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
        StockCountSessionRecord record = requireStockCountSession(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_POST);
        Long duplicateId = findIdempotentResourceId("stock-count-post", idempotencyKey);
        if (duplicateId != null) {
            return getStockCountSession(duplicateId);
        }
        ensureStatus(record.status(), "COUNTING", "Only counting stock sessions can be posted");
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
                    """, params("varianceQty", variance, "id", line.id()));
            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                jdbcTemplate.update("""
                        UPDATE inventory.stock_balance
                        SET last_count_date = :countDate, updated_at = CURRENT_TIMESTAMP
                        WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                        """, params("countDate", record.countDate(), "outletId", record.outletId(), "ingredientId", line.ingredientId()));
                continue;
            }
            ensureNonNegative(record.outletId(), line.ingredientId(), variance);
            String txnType = variance.signum() > 0 ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT";
            appendTransaction(
                    record.regionId(),
                    record.outletId(),
                    line.ingredientId(),
                    variance,
                    record.countDate(),
                    txnType,
                    null,
                    "STOCK_COUNT_SESSION",
                    id.toString(),
                    principal.userId()
            );
            applyBalanceDelta(record.regionId(), record.outletId(), line.ingredientId(), variance, null, true);
            jdbcTemplate.update("""
                    UPDATE inventory.stock_balance
                    SET last_count_date = :countDate, updated_at = CURRENT_TIMESTAMP
                    WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                    """, params("countDate", record.countDate(), "outletId", record.outletId(), "ingredientId", line.ingredientId()));
        }

        jdbcTemplate.update("""
                UPDATE inventory.stock_count_session
                SET status = 'POSTED',
                    posted_at = :postedAt,
                    posted_by_user_id = :postedByUserId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "postedAt", Instant.now(clock),
                "postedByUserId", principal.userId(),
                "id", id
        ));
        recordIdempotentResource("stock-count-post", idempotencyKey, id);
        return getStockCountSession(id);
    }

    @Transactional
    public StockCountSessionResponse cancelStockCountSession(FernPrincipal principal, Long id) {
        StockCountSessionRecord record = requireStockCountSession(id);
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_STOCK_COUNT_WRITE);
        if (!List.of("DRAFT", "COUNTING").contains(record.status())) {
            throw new ConflictException("Only draft or counting stock sessions can be cancelled");
        }
        jdbcTemplate.update("""
                UPDATE inventory.stock_count_session
                SET status = 'CANCELLED', cancelled_at = :cancelledAt, cancelled_by_user_id = :cancelledByUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "cancelledAt", Instant.now(clock),
                "cancelledByUserId", principal.userId(),
                "id", id
        ));
        return getStockCountSession(id);
    }

    @Transactional
    public SaleReservationResponse reserveSale(FernPrincipal principal, SaleReservationRequest request) {
        inventoryAuthorizer.requireInternalPermission(principal, PermissionCodes.INVENTORY_INTERNAL_RESERVE);
        if (request.usageItems().isEmpty()) {
            throw new BadRequestException("Sale reservation requires usage items");
        }
        Long existingReservationId = jdbcTemplate.query("""
                SELECT id
                FROM inventory.stock_reservation
                WHERE source_order_id = :sourceOrderId AND status IN ('RESERVED', 'COMMITTED')
                """, params("sourceOrderId", request.sourceOrderId()), rs -> rs.next() ? rs.getLong("id") : null);
        if (existingReservationId != null) {
            Instant expiresAt = jdbcTemplate.query("""
                    SELECT expires_at
                    FROM inventory.stock_reservation
                    WHERE id = :id
                    """, params("id", existingReservationId), rs -> rs.next() ? instant(rs, "expires_at") : null);
            return new SaleReservationResponse(existingReservationId, expiresAt);
        }

        Map<Long, ReservationAggregate> aggregates = aggregateUsage(request.usageItems());
        for (ReservationAggregate aggregate : aggregates.values()) {
            ensureReservationAvailability(request.outletId(), aggregate.ingredientId(), aggregate.qty());
        }

        Instant expiresAt = Instant.now(clock).plusSeconds(300);
        Long reservationId = insertForId("""
                INSERT INTO inventory.stock_reservation (
                    outlet_id, business_date, source_order_id, status, expires_at, created_at
                ) VALUES (
                    :outletId, :businessDate, :sourceOrderId, 'RESERVED', :expiresAt, CURRENT_TIMESTAMP
                )
                """, params(
                "outletId", request.outletId(),
                "businessDate", request.businessDate(),
                "sourceOrderId", request.sourceOrderId(),
                "expiresAt", expiresAt
        ));
        for (ReservationAggregate aggregate : aggregates.values()) {
            jdbcTemplate.update("""
                    INSERT INTO inventory.stock_reservation_line (
                        reservation_id, ingredient_id, ingredient_code, ingredient_name, uom_code, qty, created_at
                    ) VALUES (
                        :reservationId, :ingredientId, :ingredientCode, :ingredientName, :uomCode, :qty, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "reservationId", reservationId,
                    "ingredientId", aggregate.ingredientId(),
                    "ingredientCode", aggregate.ingredientCode(),
                    "ingredientName", aggregate.ingredientName(),
                    "uomCode", aggregate.uomCode(),
                    "qty", aggregate.qty()
            ));
            applyReservationDelta(request.outletId(), aggregate.ingredientId(), aggregate.qty());
        }
        return new SaleReservationResponse(reservationId, expiresAt);
    }

    @Transactional
    public void consumeSaleCompleted(PosSaleCompletedEvent event) {
        if (!beginInbox(event.eventId(), "pos-service", event.eventType(), event.saleOrderId().toString(), event)) {
            return;
        }
        try {
            Long reservationId = event.reservationId();
            if (reservationId == null) {
                throw new BadRequestException("Missing reservationId on pos.sale.completed");
            }
            String reservationStatus = jdbcTemplate.query("""
                    SELECT status
                    FROM inventory.stock_reservation
                    WHERE id = :id
                    """, params("id", reservationId), rs -> rs.next() ? rs.getString("status") : null);
            if (reservationStatus == null) {
                throw new ResourceNotFoundException("Reservation not found for sale completion");
            }
            if ("COMMITTED".equals(reservationStatus)) {
                markInboxProcessed(event.eventId());
                return;
            }
            List<RecipeUsageItem> usageItems = event.recipeUsageItems();
            Map<Long, ReservationAggregate> aggregates = aggregateUsageFromRecipeItems(usageItems);
            for (ReservationAggregate aggregate : aggregates.values()) {
                appendTransaction(
                        event.regionId(),
                        event.outletId(),
                        aggregate.ingredientId(),
                        aggregate.qty().negate(),
                        event.businessDate(),
                        "SALE_USAGE",
                        null,
                        "SALE_ORDER",
                        event.saleOrderId().toString(),
                        event.completedByUserId()
                );
                commitReservationDelta(event.outletId(), aggregate.ingredientId(), aggregate.qty());
            }
            jdbcTemplate.update("""
                    UPDATE inventory.stock_reservation
                    SET status = 'COMMITTED', committed_at = :committedAt
                    WHERE id = :id
                    """, params("committedAt", event.completedAt(), "id", reservationId));
            markInboxProcessed(event.eventId());
        } catch (RuntimeException exception) {
            markInboxFailed(event.eventId(), exception);
            throw exception;
        }
    }

    @Transactional
    public void consumeGoodsReceiptPosted(ProcurementGoodsReceiptPostedEvent event) {
        if (!beginInbox(event.eventId(), "procurement-service", event.eventType(), event.goodsReceiptId().toString(), event)) {
            return;
        }
        try {
            for (GoodsReceiptPostedLine line : event.lines()) {
                appendTransaction(
                        event.regionId(),
                        event.outletId(),
                        line.ingredientId(),
                        line.qtyReceived(),
                        event.businessDate(),
                        "PURCHASE_IN",
                        line.unitCost(),
                        "GOODS_RECEIPT",
                        event.goodsReceiptId().toString(),
                        event.postedByUserId()
                );
                applyBalanceDelta(event.regionId(), event.outletId(), line.ingredientId(), line.qtyReceived(), line.unitCost(), false);
            }
            markInboxProcessed(event.eventId());
        } catch (RuntimeException exception) {
            markInboxFailed(event.eventId(), exception);
            throw exception;
        }
    }

    private List<StockCountLineRecord> queryStockCountLines(Long sessionId) {
        return jdbcTemplate.query("""
                SELECT id, ingredient_id, system_qty, actual_qty, variance_qty, note
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId
                ORDER BY ingredient_id
                """, params("sessionId", sessionId), (rs, rowNum) -> new StockCountLineRecord(
                rs.getLong("id"),
                rs.getLong("ingredient_id"),
                rs.getBigDecimal("system_qty"),
                rs.getBigDecimal("actual_qty"),
                rs.getBigDecimal("variance_qty"),
                rs.getString("note")
        ));
    }

    private Optional<BigDecimal> currentSystemQty(Long sessionId, Long ingredientId) {
        return jdbcTemplate.query("""
                SELECT system_qty
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId AND ingredient_id = :ingredientId
                """, params("sessionId", sessionId, "ingredientId", ingredientId), rs -> rs.next()
                ? Optional.ofNullable(rs.getBigDecimal("system_qty"))
                : Optional.empty());
    }

    private BigDecimal currentOnHand(Long outletId, Long ingredientId) {
        BigDecimal result = jdbcTemplate.query("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, params("outletId", outletId, "ingredientId", ingredientId), rs -> rs.next() ? rs.getBigDecimal("qty_on_hand") : null);
        return result == null ? BigDecimal.ZERO : result;
    }

    private void ensureReservationAvailability(Long outletId, Long ingredientId, BigDecimal qty) {
        BigDecimal available = jdbcTemplate.query("""
                SELECT qty_available
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, params("outletId", outletId, "ingredientId", ingredientId), rs -> rs.next() ? rs.getBigDecimal("qty_available") : null);
        if (available == null || available.compareTo(qty) < 0) {
            throw new ConflictException("Insufficient available stock for ingredient " + ingredientId);
        }
    }

    private void applyReservationDelta(Long outletId, Long ingredientId, BigDecimal qtyReservedDelta) {
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_reserved = qty_reserved + :qtyReservedDelta,
                    qty_available = qty_on_hand - (qty_reserved + :qtyReservedDelta),
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, params("qtyReservedDelta", qtyReservedDelta, "outletId", outletId, "ingredientId", ingredientId));
        if (updated == 0) {
            throw new ConflictException("Stock balance not found for ingredient " + ingredientId);
        }
    }

    private void commitReservationDelta(Long outletId, Long ingredientId, BigDecimal qty) {
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_on_hand = qty_on_hand - :qty,
                    qty_reserved = qty_reserved - :qty,
                    qty_available = (qty_on_hand - :qty) - (qty_reserved - :qty),
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, params("qty", qty, "outletId", outletId, "ingredientId", ingredientId));
        if (updated == 0) {
            throw new ConflictException("Stock balance not found for ingredient " + ingredientId);
        }
    }

    private void ensureNonNegative(Long outletId, Long ingredientId, BigDecimal delta) {
        BigDecimal projected = jdbcTemplate.queryForObject("""
                SELECT COALESCE(qty_on_hand, 0) + :delta
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, params("delta", delta, "outletId", outletId, "ingredientId", ingredientId), BigDecimal.class);
        BigDecimal effectiveProjected = projected == null ? delta : projected;
        if (effectiveProjected.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConflictException("Inventory would become negative for ingredient " + ingredientId);
        }
    }

    private void applyBalanceDelta(Long regionId, Long outletId, Long ingredientId, BigDecimal delta, BigDecimal unitCost, boolean overwriteLastCountDateOnly) {
        ensureBalanceRow(regionId, outletId, ingredientId);
        MapSqlParameterSource parameters = params(
                "regionId", regionId,
                "outletId", outletId,
                "ingredientId", ingredientId,
                "delta", delta,
                "unitCost", unitCost
        );
        String sql = overwriteLastCountDateOnly
                ? """
                    UPDATE inventory.stock_balance
                    SET qty_on_hand = qty_on_hand + :delta,
                        qty_available = (qty_on_hand + :delta) - qty_reserved,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                    """
                : """
                    UPDATE inventory.stock_balance
                    SET qty_on_hand = qty_on_hand + :delta,
                        qty_available = (qty_on_hand + :delta) - qty_reserved,
                        unit_cost = COALESCE(:unitCost, unit_cost),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                    """;
        jdbcTemplate.update(sql, parameters);
    }

    private void ensureBalanceRow(Long regionId, Long outletId, Long ingredientId) {
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balance (
                    region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, updated_at
                ) VALUES (
                    :regionId, :outletId, :ingredientId, 0, 0, 0, NULL, CURRENT_TIMESTAMP
                )
                ON CONFLICT (outlet_id, ingredient_id) DO NOTHING
                """, params("regionId", regionId, "outletId", outletId, "ingredientId", ingredientId));
    }

    private Long appendTransaction(
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qtyChange,
            LocalDate businessDate,
            String txnType,
            BigDecimal unitCost,
            String sourceReferenceType,
            String sourceReferenceId,
            Long createdByUserId
    ) {
        return insertForId("""
                INSERT INTO inventory.inventory_transaction (
                    region_id, outlet_id, ingredient_id, qty_change, business_date, txn_time, txn_type, unit_cost,
                    source_reference_type, source_reference_id, created_by_user_id, created_at
                ) VALUES (
                    :regionId, :outletId, :ingredientId, :qtyChange, :businessDate, :txnTime, :txnType, :unitCost,
                    :sourceReferenceType, :sourceReferenceId, :createdByUserId, CURRENT_TIMESTAMP
                )
                """, params(
                "regionId", regionId,
                "outletId", outletId,
                "ingredientId", ingredientId,
                "qtyChange", qtyChange,
                "businessDate", businessDate,
                "txnTime", Instant.now(clock),
                "txnType", txnType,
                "unitCost", unitCost,
                "sourceReferenceType", sourceReferenceType,
                "sourceReferenceId", sourceReferenceId,
                "createdByUserId", createdByUserId
        ));
    }

    private boolean beginInbox(String sourceEventId, String sourceService, String eventType, String partitionKey, Object payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO inventory.inbox_event (
                        id, source_event_id, source_service, event_type, partition_key, payload, status, received_at
                    ) VALUES (
                        :id, :sourceEventId, :sourceService, :eventType, :partitionKey, CAST(:payload AS jsonb), 'RECEIVED', CURRENT_TIMESTAMP
                    )
                    """, params(
                    "id", UUID.randomUUID(),
                    "sourceEventId", sourceEventId,
                    "sourceService", sourceService,
                    "eventType", eventType,
                    "partitionKey", partitionKey,
                    "payload", toJson(payload)
            ));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private void markInboxProcessed(String sourceEventId) {
        jdbcTemplate.update("""
                UPDATE inventory.inbox_event
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP, error_message = NULL
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId));
    }

    private void markInboxFailed(String sourceEventId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE inventory.inbox_event
                SET status = 'FAILED', error_message = :errorMessage
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId, "errorMessage", exception.getMessage()));
    }

    private Long findIdempotentResourceId(String operation, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT resource_id
                FROM inventory.idempotency_request
                WHERE operation = :operation AND idempotency_key = :idempotencyKey
                """, params("operation", operation, "idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("resource_id") : null);
    }

    private void recordIdempotentResource(String operation, String idempotencyKey, Long resourceId) {
        jdbcTemplate.update("""
                INSERT INTO inventory.idempotency_request (operation, idempotency_key, resource_id, created_at)
                VALUES (:operation, :idempotencyKey, :resourceId, CURRENT_TIMESTAMP)
                ON CONFLICT (operation, idempotency_key) DO NOTHING
                """, params("operation", operation, "idempotencyKey", idempotencyKey, "resourceId", resourceId));
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private StockAdjustmentRecord requireStockAdjustmentRecord(Long id) {
        StockAdjustmentRecord record = jdbcTemplate.query("""
                SELECT id, status, region_id, outlet_id, ingredient_id, adjustment_direction, qty, business_date, reason, note
                FROM inventory.stock_adjustment
                WHERE id = :id
                """, params("id", id), rs -> rs.next()
                ? new StockAdjustmentRecord(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getLong("ingredient_id"),
                        rs.getString("adjustment_direction"),
                        rs.getBigDecimal("qty"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getString("reason"),
                        rs.getString("note")
                )
                : null);
        if (record == null) {
            throw new ResourceNotFoundException("Stock adjustment not found");
        }
        return record;
    }

    private StockCountSessionRecord requireStockCountSession(Long id) {
        StockCountSessionRecord record = jdbcTemplate.query("""
                SELECT id, status, region_id, outlet_id, count_date
                FROM inventory.stock_count_session
                WHERE id = :id
                """, params("id", id), rs -> rs.next()
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

    private WasteRecord requireWasteRecordRecord(Long id) {
        WasteRecord record = jdbcTemplate.query("""
                SELECT id, status, region_id, outlet_id, ingredient_id, qty, business_date, reason, note
                FROM inventory.waste_record
                WHERE id = :id
                """, params("id", id), rs -> rs.next()
                ? new WasteRecord(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getLong("ingredient_id"),
                        rs.getBigDecimal("qty"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getString("reason"),
                        rs.getString("note")
                )
                : null);
        if (record == null) {
            throw new ResourceNotFoundException("Waste record not found");
        }
        return record;
    }

    private StockAdjustmentResponse getStockAdjustment(Long id) {
        StockAdjustmentResponse response = jdbcTemplate.query("""
                SELECT id, status, region_id, outlet_id, ingredient_id, adjustment_direction, qty,
                       business_date, reason, note, inventory_transaction_id, posted_at
                FROM inventory.stock_adjustment
                WHERE id = :id
                """, params("id", id), rs -> rs.next()
                ? new StockAdjustmentResponse(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getLong("ingredient_id"),
                        rs.getString("adjustment_direction"),
                        rs.getBigDecimal("qty"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getString("reason"),
                        rs.getString("note"),
                        rs.getObject("inventory_transaction_id", Long.class),
                        instant(rs, "posted_at")
                )
                : null);
        if (response == null) {
            throw new ResourceNotFoundException("Stock adjustment not found");
        }
        return response;
    }

    private WasteRecordResponse getWasteRecord(Long id) {
        WasteRecordResponse response = jdbcTemplate.query("""
                SELECT id, status, region_id, outlet_id, ingredient_id, qty, business_date, reason, note,
                       inventory_transaction_id, posted_at
                FROM inventory.waste_record
                WHERE id = :id
                """, params("id", id), rs -> rs.next()
                ? new WasteRecordResponse(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getLong("region_id"),
                        rs.getLong("outlet_id"),
                        rs.getLong("ingredient_id"),
                        rs.getBigDecimal("qty"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getString("reason"),
                        rs.getString("note"),
                        rs.getObject("inventory_transaction_id", Long.class),
                        instant(rs, "posted_at")
                )
                : null);
        if (response == null) {
            throw new ResourceNotFoundException("Waste record not found");
        }
        return response;
    }

    private StockCountSessionResponse getStockCountSession(Long id) {
        SessionProjection session = jdbcTemplate.query("""
                SELECT id, status, region_id, outlet_id, count_date, note, started_at, posted_at
                FROM inventory.stock_count_session
                WHERE id = :id
                """, params("id", id), rs -> rs.next()
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
        List<StockCountLineResponse> lines = jdbcTemplate.query("""
                SELECT ingredient_id, system_qty, actual_qty, variance_qty, note
                FROM inventory.stock_count_line
                WHERE stock_count_session_id = :sessionId
                ORDER BY ingredient_id
                """, params("sessionId", id), (rs, rowNum) -> new StockCountLineResponse(
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

    private Map<Long, ReservationAggregate> aggregateUsage(List<SaleReservationItem> usageItems) {
        Map<Long, ReservationAggregate> aggregates = new LinkedHashMap<>();
        for (SaleReservationItem item : usageItems) {
            aggregates.compute(item.ingredientId(), (ingredientId, existing) -> {
                if (existing == null) {
                    return new ReservationAggregate(item.ingredientId(), item.ingredientCode(), item.ingredientName(), item.uomCode(), item.qty());
                }
                return new ReservationAggregate(
                        existing.ingredientId(),
                        Optional.ofNullable(existing.ingredientCode()).orElse(item.ingredientCode()),
                        Optional.ofNullable(existing.ingredientName()).orElse(item.ingredientName()),
                        Optional.ofNullable(existing.uomCode()).orElse(item.uomCode()),
                        existing.qty().add(item.qty())
                );
            });
        }
        return aggregates;
    }

    private Map<Long, ReservationAggregate> aggregateUsageFromRecipeItems(List<RecipeUsageItem> usageItems) {
        List<SaleReservationItem> reservationItems = usageItems.stream()
                .map(item -> new SaleReservationItem(item.ingredientId(), item.ingredientCode(), item.ingredientName(), item.uomCode(), item.qty()))
                .toList();
        return aggregateUsage(reservationItems);
    }

    private void validateDirection(String adjustmentDirection) {
        if (!List.of("IN", "OUT").contains(adjustmentDirection)) {
            throw new BadRequestException("adjustmentDirection must be IN or OUT");
        }
    }

    private void ensureStatus(String actualStatus, String expectedStatus, String message) {
        if (!expectedStatus.equals(actualStatus)) {
            throw new ConflictException(message);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private Long insertForId(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value == null) {
                parameters.addValue((String) values[index], null, Types.NULL);
            } else if (value instanceof Instant instant) {
                parameters.addValue((String) values[index], OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
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

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record StockAdjustmentRecord(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            Long ingredientId,
            String adjustmentDirection,
            BigDecimal qty,
            LocalDate businessDate,
            String reason,
            String note
    ) {
    }

    private record WasteRecord(
            Long id,
            String status,
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qty,
            LocalDate businessDate,
            String reason,
            String note
    ) {
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

    private record ReservationAggregate(
            Long ingredientId,
            String ingredientCode,
            String ingredientName,
            String uomCode,
            BigDecimal qty
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
}
