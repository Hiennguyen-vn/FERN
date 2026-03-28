package com.fern.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.ExceptionSummaries;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class InventoryRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InventoryRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    Long appendTransaction(
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

    void applyBalanceDelta(Long regionId, Long outletId, Long ingredientId, BigDecimal delta, BigDecimal unitCost, boolean overwriteLastCountDateOnly) {
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

    Long insertForId(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    MapSqlParameterSource params(Object... values) {
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

    boolean beginInbox(String sourceEventId, String sourceService, String eventType, String partitionKey, Object payload) {
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

    void markInboxProcessed(String sourceEventId) {
        jdbcTemplate.update("""
                UPDATE inventory.inbox_event
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP, error_message = NULL
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId));
    }

    void markInboxFailed(String sourceEventId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE inventory.inbox_event
                SET status = 'FAILED', error_message = :errorMessage
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId, "errorMessage", ExceptionSummaries.safeSummary(exception)));
    }

    Long findIdempotentResourceId(String operation, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT resource_id
                FROM inventory.idempotency_request
                WHERE operation = :operation AND idempotency_key = :idempotencyKey
                """, params("operation", operation, "idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("resource_id") : null);
    }

    void recordIdempotentResource(String operation, String idempotencyKey, Long resourceId) {
        jdbcTemplate.update("""
                INSERT INTO inventory.idempotency_request (operation, idempotency_key, resource_id, created_at)
                VALUES (:operation, :idempotencyKey, :resourceId, CURRENT_TIMESTAMP)
                ON CONFLICT (operation, idempotency_key) DO NOTHING
                """, params("operation", operation, "idempotencyKey", idempotencyKey, "resourceId", resourceId));
    }

    BigDecimal currentUnitCost(Long outletId, Long ingredientId) {
        return jdbcTemplate.query("""
                SELECT unit_cost
                FROM inventory.stock_balance
                WHERE outlet_id = :outletId AND ingredient_id = :ingredientId
                """, params("outletId", outletId, "ingredientId", ingredientId), rs -> rs.next() ? rs.getBigDecimal("unit_cost") : null);
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

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }
}
