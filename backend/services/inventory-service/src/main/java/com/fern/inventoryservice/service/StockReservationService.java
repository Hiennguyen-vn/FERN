package com.fern.inventoryservice.service;

import com.fern.inventoryservice.config.InventoryProperties;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;
    private final InventoryProperties inventoryProperties;
    private final Clock clock;

    public StockReservationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository,
            InventoryProperties inventoryProperties,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
        this.inventoryProperties = inventoryProperties;
        this.clock = clock;
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
        lockReservationBalances(request.outletId(), aggregates.values().stream()
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
            applyReservationDelta(request.outletId(), aggregate.ingredientId(), aggregate.qty());
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

    void commitSaleCompletion(PosSaleCompletedEvent event) {
        Long reservationId = event.reservationId();
        if (reservationId == null) {
            throw new BadRequestException("Missing reservationId on pos.sale.completed");
        }
        ReservationRecord reservation = requireReservationForUpdate(reservationId);
        if (StockReservationStatus.COMMITTED.name().equals(reservation.status())) {
            return;
        }
        Map<Long, ReservationAggregate> aggregates = aggregateUsageFromRecipeItems(event.recipeUsageItems());
        lockReservationBalances(event.outletId(), aggregates.values().stream()
                .map(ReservationAggregate::ingredientId)
                .toList());
        for (ReservationAggregate aggregate : aggregates.values()) {
            inventoryRepository.appendTransaction(
                    event.regionId(),
                    event.outletId(),
                    aggregate.ingredientId(),
                    aggregate.qty().negate(),
                    event.businessDate(),
                    InventoryTxnType.SALE_USAGE.name(),
                    null,
                    "SALE_ORDER",
                    event.saleOrderId().toString(),
                    event.completedByUserId()
            );
            commitReservationDelta(event.outletId(), aggregate.ingredientId(), aggregate.qty());
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

    private void applyReservationDelta(Long outletId, Long ingredientId, BigDecimal qtyReservedDelta) {
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_reserved = qty_reserved + :qtyReservedDelta,
                    qty_available = qty_available - :qtyReservedDelta,
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                  AND qty_available >= :qtyReservedDelta
                """, inventoryRepository.params("qtyReservedDelta", qtyReservedDelta, "outletId", outletId, "ingredientId", ingredientId));
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
        int updated = jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_on_hand = qty_on_hand - :qty,
                    qty_reserved = qty_reserved - :qty,
                    qty_available = (qty_on_hand - :qty) - (qty_reserved - :qty),
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, inventoryRepository.params("qty", qty, "outletId", outletId, "ingredientId", ingredientId));
        if (updated == 0) {
            throw new ConflictException("Stock balance not found for ingredient " + ingredientId);
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
                SELECT ingredient_id, qty
                FROM inventory.stock_reservation_line
                WHERE reservation_id = :reservationId
                ORDER BY ingredient_id
                """, inventoryRepository.params("reservationId", reservationId), (rs, rowNum) -> new ReservationLineRecord(
                rs.getLong("ingredient_id"),
                rs.getBigDecimal("qty")
        ));
    }

    private void clearReservationLines(Long reservationId) {
        jdbcTemplate.update("""
                DELETE FROM inventory.stock_reservation_line
                WHERE reservation_id = :reservationId
                """, inventoryRepository.params("reservationId", reservationId));
    }

    private void releaseReservation(ReservationRecord reservation) {
        List<ReservationLineRecord> lines = queryReservationLines(reservation.id());
        lockReservationBalances(reservation.outletId(), lines.stream()
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

    private void lockReservationBalances(Long outletId, List<Long> ingredientIds) {
        ingredientIds.stream()
                .distinct()
                .sorted()
                .forEach(ingredientId -> inventoryRepository.lockExistingStockBalance(outletId, ingredientId));
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
            BigDecimal qty
    ) {
    }
}
