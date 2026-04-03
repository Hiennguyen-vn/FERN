package com.fern.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.SnowflakeIdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared infrastructure for all event projectors.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Landing zone management (idempotent ingestion via event_landing table)</li>
 *   <li>Projection lag tracking (Micrometer gauge)</li>
 *   <li>JDBC parameter and JSON utility methods</li>
 * </ul>
 *
 * <p>Extracted from the original {@code ReportService} (1,073 LoC God class) to
 * isolate the cross-cutting ingestion infrastructure from domain-specific projection logic.
 */
@Component
public class ReportIngestionSupport {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AtomicLong projectionLagMillis;

    public ReportIngestionSupport(
            @Qualifier("namedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator,
            Clock clock,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.projectionLagMillis = meterRegistry.gauge("fern_projection_consumer_lag", new AtomicLong(0));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Wraps a projection work unit inside a landing-zone–guarded transaction.
     * The landing zone ensures at-most-once semantics: if the same sourceEventId
     * or idempotencyKey has already been processed, the work is skipped.
     */
    public void ingestWithLanding(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload,
            Runnable work
    ) {
        ingestWithLanding(List.of(), sourceEventId, sourceService, eventType, occurredAt, idempotencyKey, topic, payload, work);
    }

    public void ingestWithLanding(
            List<String> datasets,
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload,
            Runnable work
    ) {
        if (!Boolean.TRUE.equals(transactionTemplate.execute(status ->
                beginLanding(sourceEventId, sourceService, eventType, occurredAt, idempotencyKey, topic, payload)))) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                work.run();
                markLandingProcessed(sourceEventId);
                markProjectionSuccess(datasets, occurredAt);
            });
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                markLandingFailed(sourceEventId, exception);
                markProjectionFailure(datasets, occurredAt);
            });
            throw exception;
        }
    }

    public void updateProjectionLag(Instant occurredAt) {
        projectionLagMillis.set(Math.max(0, java.time.Duration.between(occurredAt, clock.instant()).toMillis()));
    }

    // ── Accessors for projectors ──────────────────────────────────────────────

    public NamedParameterJdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    public SnowflakeIdGenerator idGenerator() {
        return idGenerator;
    }

    public void upsertInventoryStockSnapshot(
            Long regionId,
            Long outletId,
            Long ingredientId,
            BigDecimal qtyDelta,
            BigDecimal unitCost,
            java.time.LocalDate lastCountDate,
            Instant occurredAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO report.inventory_stock_snapshot (
                    snapshot_id, region_id, outlet_id, ingredient_id, qty_on_hand, unit_cost, last_count_date, last_movement_at, updated_at
                ) VALUES (
                    :snapshotId, :regionId, :outletId, :ingredientId, :qtyOnHand, :unitCost, :lastCountDate, :lastMovementAt, CURRENT_TIMESTAMP
                )
                ON CONFLICT (outlet_id, ingredient_id) DO UPDATE
                SET region_id = EXCLUDED.region_id,
                    qty_on_hand = report.inventory_stock_snapshot.qty_on_hand + EXCLUDED.qty_on_hand,
                    unit_cost = COALESCE(EXCLUDED.unit_cost, report.inventory_stock_snapshot.unit_cost),
                    last_count_date = COALESCE(EXCLUDED.last_count_date, report.inventory_stock_snapshot.last_count_date),
                    last_movement_at = CASE
                        WHEN report.inventory_stock_snapshot.last_movement_at IS NULL THEN EXCLUDED.last_movement_at
                        WHEN EXCLUDED.last_movement_at IS NULL THEN report.inventory_stock_snapshot.last_movement_at
                        ELSE GREATEST(report.inventory_stock_snapshot.last_movement_at, EXCLUDED.last_movement_at)
                    END,
                    updated_at = CURRENT_TIMESTAMP
                """, params(
                "snapshotId", idGenerator.nextId(),
                "regionId", regionId,
                "outletId", outletId,
                "ingredientId", ingredientId,
                "qtyOnHand", qtyDelta == null ? BigDecimal.ZERO : qtyDelta,
                "unitCost", unitCost,
                "lastCountDate", lastCountDate,
                "lastMovementAt", occurredAt
        ));
    }

    // ── Utility methods ───────────────────────────────────────────────────────

    public MapSqlParameterSource params(Object... values) {
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

    public String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to serialize payload");
        }
    }

    public BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    public Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private void markProjectionSuccess(List<String> datasets, Instant occurredAt) {
        for (String dataset : distinctDatasets(datasets)) {
            jdbcTemplate.update("""
                    INSERT INTO report.projection_watermark (
                        dataset, last_occurred_at, last_ingested_at, failed_landing_count, updated_at
                    ) VALUES (
                        :dataset, :lastOccurredAt, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (dataset) DO UPDATE
                    SET last_occurred_at = CASE
                            WHEN report.projection_watermark.last_occurred_at IS NULL THEN EXCLUDED.last_occurred_at
                            ELSE GREATEST(report.projection_watermark.last_occurred_at, EXCLUDED.last_occurred_at)
                        END,
                        last_ingested_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    """, params(
                    "dataset", dataset,
                    "lastOccurredAt", occurredAt
            ));
        }
    }

    private void markProjectionFailure(List<String> datasets, Instant occurredAt) {
        for (String dataset : distinctDatasets(datasets)) {
            jdbcTemplate.update("""
                    INSERT INTO report.projection_watermark (
                        dataset, last_occurred_at, last_ingested_at, failed_landing_count, updated_at
                    ) VALUES (
                        :dataset, :lastOccurredAt, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (dataset) DO UPDATE
                    SET last_occurred_at = CASE
                            WHEN report.projection_watermark.last_occurred_at IS NULL THEN EXCLUDED.last_occurred_at
                            ELSE GREATEST(report.projection_watermark.last_occurred_at, EXCLUDED.last_occurred_at)
                        END,
                        last_ingested_at = CURRENT_TIMESTAMP,
                        failed_landing_count = report.projection_watermark.failed_landing_count + 1,
                        updated_at = CURRENT_TIMESTAMP
                    """, params(
                    "dataset", dataset,
                    "lastOccurredAt", occurredAt
            ));
        }
    }

    private List<String> distinctDatasets(List<String> datasets) {
        if (datasets == null || datasets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String dataset : datasets) {
            if (dataset != null && !dataset.isBlank()) {
                normalized.add(dataset);
            }
        }
        return List.copyOf(normalized);
    }

    // ── Landing zone internals ────────────────────────────────────────────────

    private boolean beginLanding(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload
    ) {
        lockLandingKeys(sourceEventId, idempotencyKey);
        LandingRecord existing = findLandingRecord(sourceEventId, idempotencyKey);
        if (existing != null) {
            requireMatchingLanding(existing, sourceService, eventType, occurredAt, idempotencyKey, topic, payload);
            // RECEIVED means the landing row was persisted but the projection work transaction
            // never committed (e.g. app crashed between the two transactions). Treat it the same
            // as FAILED so that re-delivery of the Kafka message can complete the projection.
            if (sourceEventId.equals(existing.sourceEventId())
                    && ("FAILED".equals(existing.status()) || "RECEIVED".equals(existing.status()))) {
                jdbcTemplate.update("""
                        UPDATE raw_events.event_landing
                        SET source_service = :sourceService,
                            event_type = :eventType,
                            occurred_at = :occurredAt,
                            ingested_at = CURRENT_TIMESTAMP,
                            idempotency_key = :idempotencyKey,
                            kafka_topic = :kafkaTopic,
                            payload = CAST(:payload AS jsonb),
                            status = 'RECEIVED',
                            processed_at = NULL,
                            error_message = NULL
                        WHERE source_event_id = :sourceEventId
                        """, params(
                        "sourceEventId", existing.sourceEventId(),
                        "sourceService", sourceService,
                        "eventType", eventType,
                        "occurredAt", occurredAt,
                        "idempotencyKey", idempotencyKey,
                        "kafkaTopic", topic,
                        "payload", payload
                ));
                return true;
            }
            return false;
        }
        jdbcTemplate.update("""
                INSERT INTO raw_events.event_landing (
                    landing_id, source_event_id, source_service, event_type, occurred_at, ingested_at,
                    idempotency_key, kafka_topic, payload, status
                ) VALUES (
                    :landingId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP,
                    :idempotencyKey, :kafkaTopic, CAST(:payload AS jsonb), 'RECEIVED'
                )
                """, params(
                "landingId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey,
                "kafkaTopic", topic,
                "payload", payload
        ));
        return true;
    }

    private void markLandingProcessed(String sourceEventId) {
        jdbcTemplate.update("""
                UPDATE raw_events.event_landing
                SET status = 'PROCESSED',
                    processed_at = CURRENT_TIMESTAMP,
                    error_message = NULL
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId));
    }

    private void markLandingFailed(String sourceEventId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE raw_events.event_landing
                SET status = 'FAILED',
                    error_message = :errorMessage
                WHERE source_event_id = :sourceEventId
                """, params(
                "sourceEventId", sourceEventId,
                "errorMessage", ExceptionSummaries.safeSummary(exception)
        ));
    }

    private void lockLandingKeys(String sourceEventId, String idempotencyKey) {
        List<String> lockKeys = new ArrayList<>();
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            lockKeys.add("source:" + sourceEventId);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            lockKeys.add("idempotency:" + idempotencyKey);
        }
        lockKeys.stream()
                .distinct()
                .sorted()
                .forEach(lockKey -> jdbcTemplate.query(
                        "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                        params("lockKey", lockKey),
                        rs -> null
                ));
    }

    private LandingRecord findLandingRecord(String sourceEventId, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT source_event_id, source_service, event_type, occurred_at, idempotency_key, kafka_topic,
                       payload::text AS payload, status
                FROM raw_events.event_landing
                WHERE source_event_id = :sourceEventId
                   OR idempotency_key = :idempotencyKey
                ORDER BY landing_id
                LIMIT 1
                """, params(
                "sourceEventId", sourceEventId,
                "idempotencyKey", idempotencyKey
        ), rs -> rs.next()
                ? new LandingRecord(
                        rs.getString("source_event_id"),
                        rs.getString("source_service"),
                        rs.getString("event_type"),
                        instant(rs, "occurred_at"),
                        rs.getString("idempotency_key"),
                        rs.getString("kafka_topic"),
                        rs.getString("payload"),
                        rs.getString("status"))
                : null);
    }

    private void requireMatchingLanding(
            LandingRecord existing,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload
    ) {
        if (!Objects.equals(existing.sourceService(), sourceService)
                || !Objects.equals(existing.eventType(), eventType)
                || !Objects.equals(existing.occurredAt(), occurredAt)
                || !Objects.equals(existing.idempotencyKey(), idempotencyKey)
                || !Objects.equals(existing.kafkaTopic(), topic)
                || !jsonEquals(existing.payload(), payload)) {
            throw new IllegalStateException("Report landing idempotency conflict");
        }
    }

    private boolean jsonEquals(String left, String right) {
        try {
            var leftNode = objectMapper.readTree(left);
            var rightNode = objectMapper.readTree(right);
            if (leftNode.isObject()) {
                leftNode = leftNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) leftNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            if (rightNode.isObject()) {
                rightNode = rightNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) rightNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to compare report landing payload", exception);
        }
    }

    private record LandingRecord(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String kafkaTopic,
            String payload,
            String status
    ) {
    }
}
