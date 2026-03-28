package com.fern.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.inventoryservice.dto.InventoryCommands.CreateWasteRecordRequest;
import com.fern.inventoryservice.dto.InventoryResponses.WasteRecordResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
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
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class WasteRecordService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;
    private final InventoryOrgClient inventoryOrgClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WasteRecordService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository,
            InventoryOrgClient inventoryOrgClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
        this.inventoryOrgClient = inventoryOrgClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
        inventoryAuthorizer.requireOutletAccess(principal, record.outletId(), PermissionCodes.INVENTORY_WASTE_WRITE);
        Long duplicateId = inventoryRepository.findIdempotentResourceId("waste-record-post", idempotencyKey);
        if (duplicateId != null) {
            return getWasteRecord(duplicateId);
        }
        ensureStatus(record.status(), WasteRecordStatus.DRAFT.name(), "Only draft waste records can be posted");
        BigDecimal signedQty = record.qty().negate();
        ensureNonNegative(record.outletId(), record.ingredientId(), signedQty);
        BigDecimal unitCost = inventoryRepository.currentUnitCost(record.outletId(), record.ingredientId());
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
        inventoryRepository.recordIdempotentResource("waste-record-post", idempotencyKey, id);
        enqueueWasteRecordPosted(record, principal, signedQty, unitCost);
        return getWasteRecord(id);
    }

    @Transactional
    public WasteRecordResponse cancelWasteRecord(FernPrincipal principal, Long id) {
        WasteRecord record = requireWasteRecordForUpdate(id);
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
        return getWasteRecord(id);
    }

    public WasteRecordResponse getWasteRecord(Long id) {
        WasteRecordResponse response = jdbcTemplate.query("""
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

    private void ensureNonNegative(Long outletId, Long ingredientId, BigDecimal delta) {
        BigDecimal projected = jdbcTemplate.query("""
                SELECT COALESCE(qty_on_hand, 0) + :delta AS projected_qty
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, inventoryRepository.params("delta", delta, "outletId", outletId, "ingredientId", ingredientId),
                rs -> rs.next() ? rs.getBigDecimal("projected_qty") : null);
        BigDecimal effectiveProjected = projected == null ? delta : projected;
        if (effectiveProjected.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConflictException("Inventory would become negative for ingredient " + ingredientId);
        }
    }

    private WasteRecord requireWasteRecordForUpdate(Long id) {
        WasteRecord record = jdbcTemplate.query("""
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

    private void enqueueWasteRecordPosted(WasteRecord record, FernPrincipal principal, BigDecimal qtyChange, BigDecimal unitCost) {
        WasteRecordPostedEvent event = new WasteRecordPostedEvent(
                UUID.randomUUID().toString(),
                "inventory.waste.posted",
                clock.instant(),
                "inventory-service",
                currentCorrelationId(),
                UUID.randomUUID().toString(),
                record.id(),
                record.regionId(),
                record.outletId(),
                record.ingredientId(),
                record.businessDate(),
                clock.instant(),
                principal.userId(),
                record.reason(),
                qtyChange,
                unitCost,
                "WASTE_RECORD",
                record.id().toString()
        );
        enqueueOutbox("WASTE_RECORD", record.id().toString(), event.eventType(), record.outletId().toString(), event);
    }

    private void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        jdbcTemplate.update("""
                INSERT INTO inventory.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, retry_count, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', 0, CURRENT_TIMESTAMP
                )
                ON CONFLICT DO NOTHING
                """, inventoryRepository.params(
                "id", UUID.randomUUID(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "partitionKey", partitionKey,
                "payload", toJson(payload)
        ));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String currentCorrelationId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest().getHeader(CorrelationId.HEADER);
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
