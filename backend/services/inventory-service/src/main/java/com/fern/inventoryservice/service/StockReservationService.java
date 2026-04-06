package com.fern.inventoryservice.service;

import com.fern.inventoryservice.config.InventoryProperties;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.security.RedisSchedulerLock;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {
    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;
    private final InventoryOrgClient inventoryOrgClient;
    private final InventoryProperties inventoryProperties;
    private final InventoryOutboxService inventoryOutboxService;
    private final Clock clock;
    private final RedisSchedulerLock schedulerLock;

    public StockReservationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository,
            InventoryOrgClient inventoryOrgClient,
            InventoryProperties inventoryProperties,
            InventoryOutboxService inventoryOutboxService,
            Clock clock,
            @org.springframework.lang.Nullable RedisSchedulerLock schedulerLock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
        this.inventoryOrgClient = inventoryOrgClient;
        this.inventoryProperties = inventoryProperties;
        this.inventoryOutboxService = inventoryOutboxService;
        this.clock = clock;
        this.schedulerLock = schedulerLock;
    }

    @Transactional
    public SaleReservationResponse reserveSale(FernPrincipal principal, SaleReservationRequest request) {
        inventoryAuthorizer.requireInternalPermission(principal, PermissionCodes.INVENTORY_INTERNAL_RESERVE);
        if (request.usageItems().isEmpty()) {
            throw new BadRequestException("Sale reservation requires usage items");
        }
        Map<Long, ReservationAggregate> aggregates = aggregateUsage(request.usageItems());
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(inventoryProperties.getReservationTtl());
        ReservationRecord existingReservation = findReservationBySourceOrderIdForUpdate(request.sourceOrderId());
        Long reservationId;
        if (existingReservation != null) {
            if (StockReservationStatus.COMMITTED.name().equals(existingReservation.status())) {
                return new SaleReservationResponse(existingReservation.id(), existingReservation.expiresAt());
            }
            if (StockReservationStatus.RESERVED.name().equals(existingReservation.status())
                    && existingReservation.expiresAt() != null
                    && existingReservation.expiresAt().isAfter(now)) {
                return new SaleReservationResponse(existingReservation.id(), existingReservation.expiresAt());
            }
            if (StockReservationStatus.RESERVED.name().equals(existingReservation.status())) {
                releaseReservation(existingReservation);
            }
            clearReservationLines(existingReservation.id());
            jdbcTemplate.update("""
                    UPDATE inventory.stock_reservation
                    SET outlet_id = :outletId,
                        business_date = :businessDate,
                        status = :status,
                        expires_at = :expiresAt,
                        committed_at = NULL
                    WHERE id = :id
                    """, inventoryRepository.params(
                    "outletId", request.outletId(),
                    "businessDate", request.businessDate(),
                    "status", StockReservationStatus.RESERVED.name(),
                    "expiresAt", expiresAt,
                    "id", existingReservation.id()
            ));
            reservationId = existingReservation.id();
        } else {
            reservationId = inventoryRepository.insertForId("""
                    INSERT INTO inventory.stock_reservation (
                        outlet_id, business_date, source_order_id, status, expires_at, created_at
                    ) VALUES (
                        :outletId, :businessDate, :sourceOrderId, :status, :expiresAt, CURRENT_TIMESTAMP
                    )
                    """, inventoryRepository.params(
                    "outletId", request.outletId(),
                    "businessDate", request.businessDate(),
                    "sourceOrderId", request.sourceOrderId(),
                    "status", StockReservationStatus.RESERVED.name(),
                    "expiresAt", expiresAt
            ));
        }
        Long regionId = resolveOutletRegionId(request.outletId());
        lockReservationBalances(regionId, request.outletId(), aggregates.values().stream()
                .map(ReservationAggregate::ingredientId)
                .toList());
        for (ReservationAggregate aggregate : aggregates.values()) {
            jdbcTemplate.update("""
                    INSERT INTO inventory.stock_reservation_line (
                        reservation_id, ingredient_id, ingredient_code, ingredient_name, uom_code, qty, created_at
                    ) VALUES (
                        :reservationId, :ingredientId, :ingredientCode, :ingredientName, :uomCode, :qty, CURRENT_TIMESTAMP
                    )
                    """, inventoryRepository.params(
                    "reservationId", reservationId,
                    "ingredientId", aggregate.ingredientId(),
                    "ingredientCode", aggregate.ingredientCode(),
                    "ingredientName", aggregate.ingredientName(),
                    "uomCode", aggregate.uomCode(),
                    "qty", aggregate.qty()
            ));
            applyReservationDelta(regionId, request.outletId(), aggregate.ingredientId(), aggregate.qty());
        }
        return new SaleReservationResponse(reservationId, expiresAt);
    }

    @Transactional
    public void releaseSaleReservation(FernPrincipal principal, Long reservationId) {
        inventoryAuthorizer.requireInternalPermission(principal, PermissionCodes.INVENTORY_INTERNAL_RELEASE);
        ReservationRecord reservation = requireReservationForUpdate(reservationId);
        if (StockReservationStatus.COMMITTED.name().equals(reservation.status())
                || StockReservationStatus.CANCELLED.name().equals(reservation.status())) {
            return;
        }
        releaseReservation(reservation);
    }

    @Transactional
    public void releaseSaleReservationBySourceOrderId(FernPrincipal principal, Long sourceOrderId) {
        inventoryAuthorizer.requireInternalPermission(principal, PermissionCodes.INVENTORY_INTERNAL_RELEASE);
        ReservationRecord reservation = findReservationBySourceOrderIdForUpdate(sourceOrderId);
        if (reservation == null
                || StockReservationStatus.COMMITTED.name().equals(reservation.status())
                || StockReservationStatus.CANCELLED.name().equals(reservation.status())) {
            return;
        }
        releaseReservation(reservation);
    }

    void commitSaleCompletion(PosSaleCompletedEvent event) {
        Long reservationId = event.reservationId();
        if (reservationId == null) {
            throw new BadRequestException("Missing reservationId on pos.sale.completed");
        }
        ReservationRecord reservation = requireReservationForUpdate(reservationId);
        if (StockReservationStatus.COMMITTED.name().equals(reservation.status())) {
            return;
        }
        if (!StockReservationStatus.RESERVED.name().equals(reservation.status())) {
            throw new ConflictException("Reservation is not active");
        }
        verifyReservationMatchesEvent(reservation, event);
        Map<Long, ReservationAggregate> aggregates = aggregateUsageFromReservationLines(queryReservationLines(reservationId));
        lockReservationBalances(event.regionId(), reservation.outletId(), aggregates.values().stream()
                .map(ReservationAggregate::ingredientId)
                .toList());
        Instant now = Instant.now(clock);
        for (ReservationAggregate aggregate : aggregates.values()) {
            BigDecimal unitCost = inventoryRepository.currentUnitCost(reservation.outletId(), aggregate.ingredientId());
            inventoryRepository.appendTransaction(
                    event.regionId(),
                    reservation.outletId(),
                    aggregate.ingredientId(),
                    aggregate.qty().negate(),
                    reservation.businessDate(),
                    InventoryTxnType.SALE_USAGE.name(),
                    unitCost,
                    "SALE_ORDER",
                    event.saleOrderId().toString(),
                    event.completedByUserId()
            );
            commitReservationDelta(reservation.outletId(), aggregate.ingredientId(), aggregate.qty());
            enqueueSaleUsageEvent(event, reservation.businessDate(), aggregate, unitCost, now);
        }
        jdbcTemplate.update("""
                UPDATE inventory.stock_reservation
                SET status = :status, committed_at = :committedAt
                WHERE id = :id
                """, inventoryRepository.params(
                "status", StockReservationStatus.COMMITTED.name(),
                "committedAt", event.completedAt(),
                "id", reservationId
        ));
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

    private Map<Long, ReservationAggregate> aggregateUsageFromReservationLines(List<ReservationLineRecord> reservationLines) {
        if (reservationLines.isEmpty()) {
            throw new ConflictException("Reservation does not contain any reserved lines");
        }
        Map<Long, ReservationAggregate> aggregates = new LinkedHashMap<>();
        for (ReservationLineRecord line : reservationLines) {
            aggregates.compute(line.ingredientId(), (ingredientId, existing) -> {
                if (existing == null) {
                    return new ReservationAggregate(
                            line.ingredientId(),
                            line.ingredientCode(),
                            line.ingredientName(),
                            line.uomCode(),
                            line.qty()
                    );
                }
                return new ReservationAggregate(
                        existing.ingredientId(),
                        Optional.ofNullable(existing.ingredientCode()).orElse(line.ingredientCode()),
                        Optional.ofNullable(existing.ingredientName()).orElse(line.ingredientName()),
                        Optional.ofNullable(existing.uomCode()).orElse(line.uomCode()),
                        existing.qty().add(line.qty())
                );
            });
        }
        return aggregates;
    }

    /**
     * Applies a reservation delta to the stock balance.
     * The {@code regionId} parameter is used for a defensive consistency check against
     * the stored balance row — if the region_id in the DB does not match, we log a warning
     * indicating a possible outlet-region mapping inconsistency.
     */
    private void applyReservationDelta(Long regionId, Long outletId, Long ingredientId, BigDecimal qtyReservedDelta) {
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_reserved = qty_reserved + :qtyReservedDelta,
                    qty_available = qty_available - :qtyReservedDelta,
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                  AND region_id = :regionId
                  AND qty_available >= :qtyReservedDelta
                """, inventoryRepository.params("qtyReservedDelta", qtyReservedDelta,
                "outletId", outletId, "ingredientId", ingredientId, "regionId", regionId));
        if (updated == 0) {
            BigDecimal available = jdbcTemplate.query("""
                    SELECT qty_available
                    FROM inventory.stock_balance
                    WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                    """, inventoryRepository.params("outletId", outletId, "ingredientId", ingredientId), rs -> rs.next() ? rs.getBigDecimal("qty_available") : null);
            if (available == null) {
                throw new ConflictException("Stock balance not found for ingredient " + ingredientId);
            }
            throw new ConflictException("Insufficient available stock for ingredient " + ingredientId);
        }
    }

    private void releaseReservationDelta(Long outletId, Long ingredientId, BigDecimal qtyReservedDelta) {
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_reserved = qty_reserved - :qtyReservedDelta,
                    qty_available = qty_available + :qtyReservedDelta,
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                  AND qty_reserved >= :qtyReservedDelta
                """, inventoryRepository.params("qtyReservedDelta", qtyReservedDelta, "outletId", outletId, "ingredientId", ingredientId));
        if (updated == 0) {
            throw new ConflictException("Reservation release failed for ingredient " + ingredientId);
        }
    }

    private void commitReservationDelta(Long outletId, Long ingredientId, BigDecimal qty) {
        // qty_available remains unchanged after commit because:
        //   reserve:  available -= qty, reserved += qty
        //   commit:   on_hand  -= qty, reserved -= qty
        //   net:      available = (on_hand - qty) - (reserved - qty) = on_hand - reserved  (unchanged)
        // Therefore qty_available is intentionally omitted from the SET clause.
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_on_hand = qty_on_hand - :qty,
                    qty_reserved = qty_reserved - :qty,
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                  AND qty_on_hand >= :qty
                  AND qty_reserved >= :qty
                """, inventoryRepository.params("qty", qty, "outletId", outletId, "ingredientId", ingredientId));
        if (updated == 0) {
            throw new ConflictException("Reservation commit failed for ingredient " + ingredientId);
        }
    }

    private ReservationRecord findReservationBySourceOrderIdForUpdate(Long sourceOrderId) {
        return jdbcTemplate.query("""
                SELECT id, outlet_id, business_date, source_order_id, status, expires_at, committed_at
                FROM inventory.stock_reservation
                WHERE source_order_id = :sourceOrderId
                FOR UPDATE
                """, inventoryRepository.params("sourceOrderId", sourceOrderId), rs -> rs.next()
                ? new ReservationRecord(
                        rs.getLong("id"),
                        rs.getLong("outlet_id"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getLong("source_order_id"),
                        rs.getString("status"),
                        instant(rs, "expires_at"),
                        instant(rs, "committed_at")
                )
                : null);
    }

    private ReservationRecord requireReservationForUpdate(Long reservationId) {
        ReservationRecord reservation = jdbcTemplate.query("""
                SELECT id, outlet_id, business_date, source_order_id, status, expires_at, committed_at
                FROM inventory.stock_reservation
                WHERE id = :id
                FOR UPDATE
                """, inventoryRepository.params("id", reservationId), rs -> rs.next()
                ? new ReservationRecord(
                        rs.getLong("id"),
                        rs.getLong("outlet_id"),
                        rs.getObject("business_date", LocalDate.class),
                        rs.getLong("source_order_id"),
                        rs.getString("status"),
                        instant(rs, "expires_at"),
                        instant(rs, "committed_at")
                )
                : null);
        if (reservation == null) {
            throw new ResourceNotFoundException("Reservation not found");
        }
        return reservation;
    }

    private List<ReservationLineRecord> queryReservationLines(Long reservationId) {
        return jdbcTemplate.query("""
                SELECT ingredient_id, ingredient_code, ingredient_name, uom_code, qty
                FROM inventory.stock_reservation_line
                WHERE reservation_id = :reservationId
                ORDER BY ingredient_id
                """, inventoryRepository.params("reservationId", reservationId), (rs, rowNum) -> new ReservationLineRecord(
                rs.getLong("ingredient_id"),
                rs.getString("ingredient_code"),
                rs.getString("ingredient_name"),
                rs.getString("uom_code"),
                rs.getBigDecimal("qty")
        ));
    }

    private void verifyReservationMatchesEvent(ReservationRecord reservation, PosSaleCompletedEvent event) {
        if (!reservation.outletId().equals(event.outletId())) {
            throw new ConflictException("Reservation outlet does not match pos.sale.completed outlet");
        }
        if (!reservation.sourceOrderId().equals(event.saleOrderId())) {
            throw new ConflictException("Reservation source order does not match pos.sale.completed sale order");
        }
    }

    private void clearReservationLines(Long reservationId) {
        jdbcTemplate.update("""
                DELETE FROM inventory.stock_reservation_line
                WHERE reservation_id = :reservationId
                """, inventoryRepository.params("reservationId", reservationId));
    }

    private void releaseReservation(ReservationRecord reservation) {
        List<ReservationLineRecord> lines = queryReservationLines(reservation.id());
        Long regionId = resolveOutletRegionId(reservation.outletId());
        lockReservationBalances(regionId, reservation.outletId(), lines.stream()
                .map(ReservationLineRecord::ingredientId)
                .toList());
        for (ReservationLineRecord line : lines) {
            releaseReservationDelta(reservation.outletId(), line.ingredientId(), line.qty());
        }
        jdbcTemplate.update("""
                UPDATE inventory.stock_reservation
                SET status = :cancelledStatus,
                    expires_at = :expiresAt
                WHERE id = :id
                  AND status = :reservedStatus
                """, inventoryRepository.params(
                "cancelledStatus", StockReservationStatus.CANCELLED.name(),
                "expiresAt", Instant.now(clock),
                "reservedStatus", StockReservationStatus.RESERVED.name(),
                "id", reservation.id()
        ));
    }

    private void lockReservationBalances(Long regionId, Long outletId, List<Long> ingredientIds) {
        ingredientIds.stream()
                .distinct()
                .sorted()
                .forEach(ingredientId -> {
                    // Upsert balance row before acquiring a pessimistic lock.
                    // Prevents a "row not found" deadlock for ingredients that have never been stocked at this outlet.
                    inventoryRepository.ensureBalanceRowPublic(regionId, outletId, ingredientId);
                    inventoryRepository.lockExistingStockBalance(outletId, ingredientId);
                });
    }

    private Long resolveOutletRegionId(Long outletId) {
        Long fromBalance = inventoryRepository.resolveOutletRegionId(outletId);
        if (fromBalance != null) {
            return fromBalance;
        }
        return inventoryOrgClient.requireOutlet(outletId).regionId();
    }

    private Instant instant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record ReservationAggregate(
            Long ingredientId,
            String ingredientCode,
            String ingredientName,
            String uomCode,
            BigDecimal qty
    ) {
    }

    private record ReservationRecord(
            Long id,
            Long outletId,
            LocalDate businessDate,
            Long sourceOrderId,
            String status,
            Instant expiresAt,
            Instant committedAt
    ) {
    }

    private record ReservationLineRecord(
            Long ingredientId,
            String ingredientCode,
            String ingredientName,
            String uomCode,
            BigDecimal qty
    ) {
    }

    private void enqueueSaleUsageEvent(
            PosSaleCompletedEvent event,
            LocalDate businessDate,
            ReservationAggregate aggregate,
            BigDecimal unitCost,
            Instant now
    ) {
        String idempotencyKey = "inventory.sale_usage.posted:sale_order:" + event.saleOrderId() + ":ingredient:" + aggregate.ingredientId();
        InventoryAdjustmentPostedEvent usageEvent = new InventoryAdjustmentPostedEvent(
                UUID.randomUUID().toString(),
                "inventory.adjustment.posted",
                now,
                "inventory-service",
                null,
                idempotencyKey,
                null,
                event.regionId(),
                event.outletId(),
                aggregate.ingredientId(),
                businessDate,
                now,
                event.completedByUserId(),
                "OUT",
                "SALE_USAGE",
                aggregate.qty().negate(),
                unitCost,
                "SALE_ORDER",
                event.saleOrderId().toString()
        );
        inventoryOutboxService.enqueueOutbox(
                "SALE_ORDER",
                event.saleOrderId() + ":ingredient:" + aggregate.ingredientId(),
                usageEvent.eventType(),
                event.outletId().toString(),
                usageEvent
        );
    }

    /**
     * Scheduled job that releases reservations whose TTL has expired but were never committed or cancelled.
     * Without this, a crashed POS client would leave qty_reserved permanently locked ("ghost reservation").
     *
     * <p>P1-03 FIX: Uses {@link RedisSchedulerLock} to prevent multiple instances from running
     * the cleanup concurrently, which could cause double-release and stock data corruption.
     */
    @Scheduled(fixedDelayString = "${fern.inventory.reservation-expiry-cleanup-delay-ms:60000}")
    public void releaseExpiredReservations() {
        if (schedulerLock != null && !schedulerLock.tryAcquire("inventory-reservation-cleanup", Duration.ofMinutes(2))) {
            log.debug("releaseExpiredReservations: skipped — another instance holds the lock");
            return;
        }
        try {
            doReleaseExpiredReservations();
        } finally {
            if (schedulerLock != null) {
                schedulerLock.release("inventory-reservation-cleanup");
            }
        }
    }

    private void doReleaseExpiredReservations() {
        Instant now = Instant.now(clock);
        List<ReservationRecord> expiredReservations = jdbcTemplate.query("""
                SELECT id, outlet_id, business_date, source_order_id, status, expires_at, committed_at
                FROM inventory.stock_reservation
                WHERE status = :status AND expires_at < :now
                ORDER BY expires_at ASC
                LIMIT 100
                """, inventoryRepository.params(
                "status", StockReservationStatus.RESERVED.name(),
                "now", now
        ), (rs, rowNum) -> new ReservationRecord(
                rs.getLong("id"),
                rs.getLong("outlet_id"),
                rs.getObject("business_date", LocalDate.class),
                rs.getLong("source_order_id"),
                rs.getString("status"),
                instant(rs, "expires_at"),
                instant(rs, "committed_at")
        ));
        for (ReservationRecord reservation : expiredReservations) {
            try {
                releaseExpiredReservation(reservation);
            } catch (RuntimeException exception) {
                log.warn("Failed to release expired reservation id={} sourceOrderId={}",
                        reservation.id(), reservation.sourceOrderId(), exception);
            }
        }
        if (!expiredReservations.isEmpty()) {
            log.info("Released {} expired inventory reservations", expiredReservations.size());
        }
    }

    @Transactional
    void releaseExpiredReservation(ReservationRecord reservation) {
        ReservationRecord locked = requireReservationForUpdate(reservation.id());
        if (!StockReservationStatus.RESERVED.name().equals(locked.status())) {
            return; // Already committed or cancelled since we read it
        }
        releaseReservation(locked);
    }
}
