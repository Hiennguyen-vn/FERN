package com.fern.inventoryservice.service;

import com.fern.inventoryservice.dto.InventoryCommands.CreateWasteRecordRequest;
import com.fern.inventoryservice.dto.InventoryResponses.WasteRecordResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.WasteRecordPostedEvent;
import com.fern.platform.observability.CorrelationId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WasteRecordService {
    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;
    private final InventoryOutboxService inventoryOutboxService;
    private final InventoryOrgClient inventoryOrgClient;
    private final InventoryAuditService inventoryAuditService;
    private final Clock clock;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;

    public WasteRecordService(
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository,
            InventoryOutboxService inventoryOutboxService,
            InventoryOrgClient inventoryOrgClient,
            InventoryAuditService inventoryAuditService,
            Clock clock,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver
    ) {
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
        this.inventoryOutboxService = inventoryOutboxService;
        this.inventoryOrgClient = inventoryOrgClient;
        this.inventoryAuditService = inventoryAuditService;
        this.clock = clock;
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
    }

    @Transactional
    public WasteRecordResponse createWasteRecord(FernPrincipal principal, CreateWasteRecordRequest request) {
        inventoryAuthorizer.requireOutletAccess(principal, request.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        InventoryOrgClient.OutletRoute outlet = inventoryOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new BadRequestException("Region does not match the outlet route");
        }
        Long id = inventoryRepository.insertForId("""
                INSERT INTO inventory.waste_record (
                    inventory_transaction_id, status, reason, submitted_by_user_id, approved_by_user_id, created_at, updated_at,
                    region_id, outlet_id, ingredient_id, qty, business_date, note, created_by_user_id
                ) VALUES (
                    NULL, :status, :reason, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    :regionId, :outletId, :ingredientId, :qty, :businessDate, :note, :createdByUserId
                )
                """, inventoryRepository.params(
                "status", WasteRecordStatus.DRAFT.name(),
                "reason", request.reason(),
                "regionId", outlet.regionId(),
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
        WasteRecord record = requireWasteRecordForUpdate(id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(record.regionId(), record.outletId());
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        Long duplicateId = inventoryRepository.findIdempotentResourceId("waste-record-post", idempotencyKey);
        if (duplicateId != null) {
            if (!duplicateId.equals(id)) {
                throw new ConflictException("Idempotency-Key is already used for a different waste record");
            }
            return getWasteRecord(duplicateId);
        }
        Long claimedId = inventoryRepository.claimIdempotentResource("waste-record-post", idempotencyKey, id);
        if (!id.equals(claimedId)) {
            throw new ConflictException("Idempotency-Key is already used for a different waste record");
        }
        ensureStatus(record.status(), WasteRecordStatus.DRAFT.name(), "Only draft waste records can be posted");
        BigDecimal signedQty = record.qty().negate();
        InventoryRepository.StockBalanceSnapshot balance = inventoryRepository.lockStockBalance(
                record.regionId(),
                record.outletId(),
                record.ingredientId()
        );
        ensureNonNegative(balance.qtyOnHand(), signedQty, record.ingredientId());
        BigDecimal unitCost = balance.unitCost();
        Long transactionId = inventoryRepository.appendTransaction(
                record.regionId(),
                record.outletId(),
                record.ingredientId(),
                signedQty,
                record.businessDate(),
                InventoryTxnType.WASTE_OUT.name(),
                unitCost,
                "WASTE_RECORD",
                id.toString(),
                principal.userId()
        );
        inventoryRepository.applyBalanceDelta(record.regionId(), record.outletId(), record.ingredientId(), signedQty, unitCost, false);
        int updated = jdbcTemplate.update("""
                UPDATE inventory.waste_record
                SET inventory_transaction_id = :transactionId,
                    status = :postedStatus,
                    posted_at = :postedAt,
                    posted_by_user_id = :postedByUserId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :draftStatus
                """, inventoryRepository.params(
                "transactionId", transactionId,
                "postedStatus", WasteRecordStatus.POSTED.name(),
                "postedAt", Instant.now(clock),
                "postedByUserId", principal.userId(),
                "draftStatus", WasteRecordStatus.DRAFT.name(),
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only draft waste records can be posted");
        }
        WasteRecordResponse response = getWasteRecord(id);
        enqueueWasteRecordPosted(response, record, principal, signedQty, unitCost);
        // AUD-002: publish audit event for waste post with snapshot
        inventoryAuditService.publishInventoryEvent(
                "inventory.waste.posted.audit",
                principal,
                record.regionId(),
                record.outletId(),
                "POST_WASTE_RECORD",
                "WASTE_RECORD",
                id,
                Map.of("status", "DRAFT"),
                Map.of("status", "POSTED", "ingredientId", record.ingredientId(),
                       "qty", record.qty(), "unitCost", unitCost == null ? 0 : unitCost,
                       "transactionId", transactionId),
                Map.of("reason", record.reason(), "businessDate", record.businessDate().toString())
        );
        return response;
    }

    @Transactional
    public WasteRecordResponse cancelWasteRecord(FernPrincipal principal, Long id) {
        WasteRecord record = requireWasteRecordForUpdate(id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(record.regionId(), record.outletId());
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        if (!WasteRecordStatus.DRAFT.name().equals(record.status())) {
            throw new ConflictException("Only draft waste records can be cancelled");
        }
        int updated = jdbcTemplate.update("""
                UPDATE inventory.waste_record
                SET status = :cancelledStatus, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :draftStatus
                """, inventoryRepository.params(
                "cancelledStatus", WasteRecordStatus.CANCELLED.name(),
                "draftStatus", WasteRecordStatus.DRAFT.name(),
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only draft waste records can be cancelled");
        }
        // AUD-002: publish audit event for waste cancel
        inventoryAuditService.publishInventoryEvent(
                "inventory.waste.cancelled.audit",
                principal,
                record.regionId(),
                record.outletId(),
                "CANCEL_WASTE_RECORD",
                "WASTE_RECORD",
                id,
                Map.of("status", "DRAFT"),
                Map.of("status", "CANCELLED"),
                Map.of("ingredientId", record.ingredientId(), "reason", record.reason())
        );
        return getWasteRecord(id);
    }

    public WasteRecordResponse getWasteRecord(Long id) {
        WasteRecordResponse response = jdbcTemplate(null, null).query("""
                SELECT id, status, region_id, outlet_id, ingredient_id, qty, business_date, reason, note,
                       inventory_transaction_id, posted_at
                FROM inventory.waste_record
                WHERE id = :id
                """, inventoryRepository.params("id", id), rs -> rs.next()
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

    private WasteRecord requireWasteRecordForUpdate(Long id) {
        WasteRecord record = jdbcTemplate(null, null).query("""
                SELECT id, status, region_id, outlet_id, ingredient_id, qty, business_date, reason, note
                FROM inventory.waste_record
                WHERE id = :id
                FOR UPDATE
                """, inventoryRepository.params("id", id), rs -> rs.next()
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

    private void enqueueWasteRecordPosted(
            WasteRecordResponse response,
            WasteRecord record,
            FernPrincipal principal,
            BigDecimal qtyChange,
            BigDecimal unitCost
    ) {
        WasteRecordPostedEvent event = new WasteRecordPostedEvent(
                UUID.randomUUID().toString(),
                "inventory.waste.posted",
                response.postedAt(),
                "inventory-service",
                currentCorrelationId(),
                wastePostedIdempotencyKey(record.id()),
                record.id(),
                record.regionId(),
                record.outletId(),
                record.ingredientId(),
                record.businessDate(),
                response.postedAt(),
                principal.userId(),
                record.reason(),
                qtyChange,
                unitCost,
                "WASTE_RECORD",
                record.id().toString()
        );
        inventoryOutboxService.enqueueOutbox("WASTE_RECORD", record.id().toString(), event.eventType(), record.outletId().toString(), event);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String currentCorrelationId() {
        return MDC.get(CorrelationId.MDC_KEY);
    }

    private String wastePostedIdempotencyKey(Long wasteRecordId) {
        return "inventory.waste.posted:waste:" + wasteRecordId;
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
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
}
