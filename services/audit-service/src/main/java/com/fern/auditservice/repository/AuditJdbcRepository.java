package com.fern.auditservice.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.audit.SecurityEvent;
import com.fern.platform.common.SnowflakeIdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditJdbcRepository {
    private static final String AUDIT_EVENT_TABLE = "audit.audit_event";
    private static final String SECURITY_EVENT_TABLE = "audit.security_event";
    private static final String REQUEST_TRACE_TABLE = "audit.request_trace";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;

    public AuditJdbcRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    public void insertAuditEvent(AuditEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("auditEventId", idGenerator.nextId())
                .addValue("sourceEventId", event.eventId())
                .addValue("sourceService", event.sourceService())
                .addValue("module", moduleFromPayload(event.payload(), event.sourceService()))
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", timestamp(event.occurredAt()))
                .addValue("idempotencyKey", fallbackIdempotency(event.idempotencyKey(), event.eventId()))
                .addValue("correlationId", event.correlationId())
                .addValue("regionId", event.regionId())
                .addValue("outletId", event.outletId())
                .addValue("userId", event.userId())
                .addValue("action", event.action())
                .addValue("resourceType", event.resourceType())
                .addValue("resourceId", event.resourceId())
                .addValue("outcome", event.outcome())
                .addValue("oldValueJson", toJson(event.oldValue()))
                .addValue("newValueJson", toJson(event.newValue()))
                .addValue("payloadJson", toJson(event.payload()));

        jdbcTemplate.update("""
                INSERT INTO %s (
                    audit_event_id,
                    source_event_id,
                    source_service,
                    module,
                    event_type,
                    occurred_at,
                    idempotency_key,
                    correlation_id,
                    region_id,
                    outlet_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    outcome,
                    old_value,
                    new_value,
                    payload
                ) VALUES (
                    :auditEventId,
                    :sourceEventId,
                    :sourceService,
                    :module,
                    :eventType,
                    :occurredAt,
                    :idempotencyKey,
                    :correlationId,
                    :regionId,
                    :outletId,
                    :userId,
                    :action,
                    :resourceType,
                    :resourceId,
                    :outcome,
                    CAST(:oldValueJson AS jsonb),
                    CAST(:newValueJson AS jsonb),
                    CAST(:payloadJson AS jsonb)
                )
                ON CONFLICT DO NOTHING
                """.formatted(AUDIT_EVENT_TABLE), params);
    }

    public void insertSecurityEvent(SecurityEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("securityEventId", idGenerator.nextId())
                .addValue("sourceEventId", event.eventId())
                .addValue("sourceService", event.sourceService())
                .addValue("module", moduleFromPayload(event.payload(), event.sourceService()))
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", timestamp(event.occurredAt()))
                .addValue("idempotencyKey", fallbackIdempotency(event.idempotencyKey(), event.eventId()))
                .addValue("correlationId", event.correlationId())
                .addValue("userId", event.userId())
                .addValue("outcome", event.outcome())
                .addValue("failureReason", event.failureReason())
                .addValue("ipAddress", event.ipAddress())
                .addValue("userAgent", event.userAgent())
                .addValue("payloadJson", toJson(event.payload()));

        jdbcTemplate.update("""
                INSERT INTO %s (
                    security_event_id,
                    source_event_id,
                    source_service,
                    module,
                    event_type,
                    occurred_at,
                    idempotency_key,
                    correlation_id,
                    user_id,
                    outcome,
                    failure_reason,
                    ip_address,
                    user_agent,
                    payload
                ) VALUES (
                    :securityEventId,
                    :sourceEventId,
                    :sourceService,
                    :module,
                    :eventType,
                    :occurredAt,
                    :idempotencyKey,
                    :correlationId,
                    :userId,
                    :outcome,
                    :failureReason,
                    :ipAddress,
                    :userAgent,
                    CAST(:payloadJson AS jsonb)
                )
                ON CONFLICT DO NOTHING
                """.formatted(SECURITY_EVENT_TABLE), params);
    }

    public void insertRequestTrace(RequestTraceEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestTraceId", idGenerator.nextId())
                .addValue("sourceEventId", event.eventId())
                .addValue("sourceService", event.sourceService())
                .addValue("module", moduleFromPayload(event.payload(), event.sourceService()))
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", timestamp(event.occurredAt()))
                .addValue("idempotencyKey", fallbackIdempotency(event.idempotencyKey(), event.eventId()))
                .addValue("correlationId", event.correlationId())
                .addValue("requestId", event.requestId())
                .addValue("endpoint", event.endpoint())
                .addValue("method", event.method())
                .addValue("statusCode", event.statusCode())
                .addValue("durationMs", event.durationMs())
                .addValue("regionId", event.regionId())
                .addValue("outletId", event.outletId())
                .addValue("userId", event.userId())
                .addValue("payloadJson", toJson(event.payload()));

        jdbcTemplate.update("""
                INSERT INTO %s (
                    request_trace_id,
                    source_event_id,
                    source_service,
                    module,
                    event_type,
                    occurred_at,
                    idempotency_key,
                    correlation_id,
                    request_id,
                    endpoint,
                    method,
                    status_code,
                    duration_ms,
                    region_id,
                    outlet_id,
                    user_id,
                    payload
                ) VALUES (
                    :requestTraceId,
                    :sourceEventId,
                    :sourceService,
                    :module,
                    :eventType,
                    :occurredAt,
                    :idempotencyKey,
                    :correlationId,
                    :requestId,
                    :endpoint,
                    :method,
                    :statusCode,
                    :durationMs,
                    :regionId,
                    :outletId,
                    :userId,
                    CAST(:payloadJson AS jsonb)
                )
                ON CONFLICT DO NOTHING
                """.formatted(REQUEST_TRACE_TABLE), params);
    }

    public List<AuditEventRow> findAuditEvents(AuditEventFilter filter) {
        QueryParts query = auditEventQuery(filter);
        return jdbcTemplate.query(query.sql(), query.params(), (resultSet, rowNum) -> mapAuditEvent(resultSet));
    }

    public Optional<AuditEventRow> findAuditEventById(Long id) {
        return queryOptional("""
                SELECT
                    audit_event_id AS AUDIT_EVENT_ID,
                    source_event_id AS SOURCE_EVENT_ID,
                    source_service AS SOURCE_SERVICE,
                    COALESCE(module, source_service) AS MODULE,
                    event_type AS EVENT_TYPE,
                    occurred_at AS OCCURRED_AT,
                    ingested_at AS INGESTED_AT,
                    idempotency_key AS IDEMPOTENCY_KEY,
                    correlation_id AS CORRELATION_ID,
                    region_id AS REGION_ID,
                    outlet_id AS OUTLET_ID,
                    user_id AS USER_ID,
                    action AS ACTION,
                    resource_type AS RESOURCE_TYPE,
                    resource_id AS RESOURCE_ID,
                    outcome AS OUTCOME,
                    CAST(old_value AS text) AS OLD_VALUE_JSON,
                    CAST(new_value AS text) AS NEW_VALUE_JSON,
                    CAST(payload AS text) AS PAYLOAD_JSON
                FROM %s
                WHERE audit_event_id = :id
                """.formatted(AUDIT_EVENT_TABLE), new MapSqlParameterSource("id", id), this::mapAuditEvent);
    }

    public List<SecurityEventRow> findSecurityEvents(SecurityEventFilter filter) {
        QueryParts query = securityEventQuery(filter);
        return jdbcTemplate.query(query.sql(), query.params(), (resultSet, rowNum) -> mapSecurityEvent(resultSet));
    }

    public Optional<SecurityEventRow> findSecurityEventById(Long id) {
        return queryOptional("""
                SELECT
                    security_event_id AS SECURITY_EVENT_ID,
                    source_event_id AS SOURCE_EVENT_ID,
                    source_service AS SOURCE_SERVICE,
                    COALESCE(module, source_service) AS MODULE,
                    event_type AS EVENT_TYPE,
                    occurred_at AS OCCURRED_AT,
                    ingested_at AS INGESTED_AT,
                    idempotency_key AS IDEMPOTENCY_KEY,
                    correlation_id AS CORRELATION_ID,
                    user_id AS USER_ID,
                    outcome AS OUTCOME,
                    failure_reason AS FAILURE_REASON,
                    ip_address AS IP_ADDRESS,
                    user_agent AS USER_AGENT,
                    CAST(payload AS text) AS PAYLOAD_JSON
                FROM %s
                WHERE security_event_id = :id
                """.formatted(SECURITY_EVENT_TABLE), new MapSqlParameterSource("id", id), this::mapSecurityEvent);
    }

    public List<RequestTraceRow> findRequestTraces(RequestTraceFilter filter) {
        QueryParts query = requestTraceQuery(filter);
        return jdbcTemplate.query(query.sql(), query.params(), (resultSet, rowNum) -> mapRequestTrace(resultSet));
    }

    public Optional<RequestTraceRow> findRequestTraceById(Long id) {
        return queryOptional("""
                SELECT
                    request_trace_id AS REQUEST_TRACE_ID,
                    source_event_id AS SOURCE_EVENT_ID,
                    source_service AS SOURCE_SERVICE,
                    COALESCE(module, source_service) AS MODULE,
                    event_type AS EVENT_TYPE,
                    occurred_at AS OCCURRED_AT,
                    ingested_at AS INGESTED_AT,
                    idempotency_key AS IDEMPOTENCY_KEY,
                    correlation_id AS CORRELATION_ID,
                    request_id AS REQUEST_ID,
                    endpoint AS ENDPOINT,
                    method AS METHOD,
                    status_code AS STATUS_CODE,
                    duration_ms AS DURATION_MS,
                    region_id AS REGION_ID,
                    outlet_id AS OUTLET_ID,
                    user_id AS USER_ID,
                    CAST(payload AS text) AS PAYLOAD_JSON
                FROM %s
                WHERE request_trace_id = :id
                """.formatted(REQUEST_TRACE_TABLE), new MapSqlParameterSource("id", id), this::mapRequestTrace);
    }

    private QueryParts auditEventQuery(AuditEventFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", sanitizeLimit(filter.limit()));
        addCommonConditions(
                conditions,
                params,
                filter.userId(),
                filter.sourceService(),
                filter.module(),
                filter.occurredFrom(),
                filter.occurredTo(),
                filter.regionId(),
                filter.outletId(),
                filter.correlationId()
        );
        if (hasText(filter.action())) {
            conditions.add("action = :action");
            params.addValue("action", filter.action());
        }
        if (hasText(filter.resourceType())) {
            conditions.add("resource_type = :resourceType");
            params.addValue("resourceType", filter.resourceType());
        }
        if (hasText(filter.resourceId())) {
            conditions.add("resource_id = :resourceId");
            params.addValue("resourceId", filter.resourceId());
        }
        if (hasText(filter.outcome())) {
            conditions.add("outcome = :outcome");
            params.addValue("outcome", filter.outcome());
        }

        return new QueryParts("""
                SELECT
                    audit_event_id AS AUDIT_EVENT_ID,
                    source_event_id AS SOURCE_EVENT_ID,
                    source_service AS SOURCE_SERVICE,
                    COALESCE(module, source_service) AS MODULE,
                    event_type AS EVENT_TYPE,
                    occurred_at AS OCCURRED_AT,
                    ingested_at AS INGESTED_AT,
                    idempotency_key AS IDEMPOTENCY_KEY,
                    correlation_id AS CORRELATION_ID,
                    region_id AS REGION_ID,
                    outlet_id AS OUTLET_ID,
                    user_id AS USER_ID,
                    action AS ACTION,
                    resource_type AS RESOURCE_TYPE,
                    resource_id AS RESOURCE_ID,
                    outcome AS OUTCOME,
                    CAST(old_value AS text) AS OLD_VALUE_JSON,
                    CAST(new_value AS text) AS NEW_VALUE_JSON,
                    CAST(payload AS text) AS PAYLOAD_JSON
                FROM %s
                %s
                ORDER BY occurred_at DESC, audit_event_id DESC
                LIMIT :limit
                """.formatted(AUDIT_EVENT_TABLE, whereClause(conditions)), params);
    }

    private QueryParts securityEventQuery(SecurityEventFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", sanitizeLimit(filter.limit()));
        addCommonConditions(
                conditions,
                params,
                filter.userId(),
                filter.sourceService(),
                filter.module(),
                filter.occurredFrom(),
                filter.occurredTo(),
                null,
                null,
                filter.correlationId()
        );
        if (hasText(filter.eventType())) {
            conditions.add("event_type = :eventType");
            params.addValue("eventType", filter.eventType());
        }
        if (hasText(filter.outcome())) {
            conditions.add("outcome = :outcome");
            params.addValue("outcome", filter.outcome());
        }

        return new QueryParts("""
                SELECT
                    security_event_id AS SECURITY_EVENT_ID,
                    source_event_id AS SOURCE_EVENT_ID,
                    source_service AS SOURCE_SERVICE,
                    COALESCE(module, source_service) AS MODULE,
                    event_type AS EVENT_TYPE,
                    occurred_at AS OCCURRED_AT,
                    ingested_at AS INGESTED_AT,
                    idempotency_key AS IDEMPOTENCY_KEY,
                    correlation_id AS CORRELATION_ID,
                    user_id AS USER_ID,
                    outcome AS OUTCOME,
                    failure_reason AS FAILURE_REASON,
                    ip_address AS IP_ADDRESS,
                    user_agent AS USER_AGENT,
                    CAST(payload AS text) AS PAYLOAD_JSON
                FROM %s
                %s
                ORDER BY occurred_at DESC, security_event_id DESC
                LIMIT :limit
                """.formatted(SECURITY_EVENT_TABLE, whereClause(conditions)), params);
    }

    private QueryParts requestTraceQuery(RequestTraceFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", sanitizeLimit(filter.limit()));
        addCommonConditions(
                conditions,
                params,
                filter.userId(),
                filter.sourceService(),
                filter.module(),
                filter.occurredFrom(),
                filter.occurredTo(),
                filter.regionId(),
                filter.outletId(),
                filter.correlationId()
        );
        if (hasText(filter.endpoint())) {
            conditions.add("endpoint = :endpoint");
            params.addValue("endpoint", filter.endpoint());
        }
        if (hasText(filter.method())) {
            conditions.add("method = :method");
            params.addValue("method", filter.method());
        }
        if (filter.statusCode() != null) {
            conditions.add("status_code = :statusCode");
            params.addValue("statusCode", filter.statusCode());
        }

        return new QueryParts("""
                SELECT
                    request_trace_id AS REQUEST_TRACE_ID,
                    source_event_id AS SOURCE_EVENT_ID,
                    source_service AS SOURCE_SERVICE,
                    COALESCE(module, source_service) AS MODULE,
                    event_type AS EVENT_TYPE,
                    occurred_at AS OCCURRED_AT,
                    ingested_at AS INGESTED_AT,
                    idempotency_key AS IDEMPOTENCY_KEY,
                    correlation_id AS CORRELATION_ID,
                    request_id AS REQUEST_ID,
                    endpoint AS ENDPOINT,
                    method AS METHOD,
                    status_code AS STATUS_CODE,
                    duration_ms AS DURATION_MS,
                    region_id AS REGION_ID,
                    outlet_id AS OUTLET_ID,
                    user_id AS USER_ID,
                    CAST(payload AS text) AS PAYLOAD_JSON
                FROM %s
                %s
                ORDER BY occurred_at DESC, request_trace_id DESC
                LIMIT :limit
                """.formatted(REQUEST_TRACE_TABLE, whereClause(conditions)), params);
    }

    private void addCommonConditions(
            List<String> conditions,
            MapSqlParameterSource params,
            Long userId,
            String sourceService,
            String module,
            Instant occurredFrom,
            Instant occurredTo,
            Long regionId,
            Long outletId,
            String correlationId
    ) {
        if (userId != null) {
            conditions.add("user_id = :userId");
            params.addValue("userId", userId);
        }
        if (hasText(sourceService)) {
            conditions.add("source_service = :sourceService");
            params.addValue("sourceService", sourceService);
        }
        if (hasText(module)) {
            conditions.add("COALESCE(module, source_service) = :module");
            params.addValue("module", module);
        }
        if (occurredFrom != null) {
            conditions.add("occurred_at >= :occurredFrom");
            params.addValue("occurredFrom", timestamp(occurredFrom));
        }
        if (occurredTo != null) {
            conditions.add("occurred_at <= :occurredTo");
            params.addValue("occurredTo", timestamp(occurredTo));
        }
        if (regionId != null) {
            conditions.add("region_id = :regionId");
            params.addValue("regionId", regionId);
        }
        if (outletId != null) {
            conditions.add("outlet_id = :outletId");
            params.addValue("outletId", outletId);
        }
        if (hasText(correlationId)) {
            conditions.add("correlation_id = :correlationId");
            params.addValue("correlationId", correlationId);
        }
    }

    private String whereClause(List<String> conditions) {
        if (conditions.isEmpty()) {
            return "";
        }
        return "WHERE " + String.join(" AND ", conditions);
    }

    private int sanitizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private AuditEventRow mapAuditEvent(ResultSet resultSet) throws SQLException {
        return new AuditEventRow(
                resultSet.getLong("AUDIT_EVENT_ID"),
                resultSet.getString("SOURCE_EVENT_ID"),
                resultSet.getString("SOURCE_SERVICE"),
                resultSet.getString("MODULE"),
                resultSet.getString("EVENT_TYPE"),
                instant(resultSet, "OCCURRED_AT"),
                instant(resultSet, "INGESTED_AT"),
                resultSet.getString("IDEMPOTENCY_KEY"),
                resultSet.getString("CORRELATION_ID"),
                nullableLong(resultSet, "REGION_ID"),
                nullableLong(resultSet, "OUTLET_ID"),
                nullableLong(resultSet, "USER_ID"),
                resultSet.getString("ACTION"),
                resultSet.getString("RESOURCE_TYPE"),
                resultSet.getString("RESOURCE_ID"),
                resultSet.getString("OUTCOME"),
                fromJson(resultSet.getString("OLD_VALUE_JSON")),
                fromJson(resultSet.getString("NEW_VALUE_JSON")),
                fromJson(resultSet.getString("PAYLOAD_JSON"))
        );
    }

    private SecurityEventRow mapSecurityEvent(ResultSet resultSet) throws SQLException {
        return new SecurityEventRow(
                resultSet.getLong("SECURITY_EVENT_ID"),
                resultSet.getString("SOURCE_EVENT_ID"),
                resultSet.getString("SOURCE_SERVICE"),
                resultSet.getString("MODULE"),
                resultSet.getString("EVENT_TYPE"),
                instant(resultSet, "OCCURRED_AT"),
                instant(resultSet, "INGESTED_AT"),
                resultSet.getString("IDEMPOTENCY_KEY"),
                resultSet.getString("CORRELATION_ID"),
                nullableLong(resultSet, "USER_ID"),
                resultSet.getString("OUTCOME"),
                resultSet.getString("FAILURE_REASON"),
                resultSet.getString("IP_ADDRESS"),
                resultSet.getString("USER_AGENT"),
                fromJson(resultSet.getString("PAYLOAD_JSON"))
        );
    }

    private RequestTraceRow mapRequestTrace(ResultSet resultSet) throws SQLException {
        return new RequestTraceRow(
                resultSet.getLong("REQUEST_TRACE_ID"),
                resultSet.getString("SOURCE_EVENT_ID"),
                resultSet.getString("SOURCE_SERVICE"),
                resultSet.getString("MODULE"),
                resultSet.getString("EVENT_TYPE"),
                instant(resultSet, "OCCURRED_AT"),
                instant(resultSet, "INGESTED_AT"),
                resultSet.getString("IDEMPOTENCY_KEY"),
                resultSet.getString("CORRELATION_ID"),
                resultSet.getString("REQUEST_ID"),
                resultSet.getString("ENDPOINT"),
                resultSet.getString("METHOD"),
                resultSet.getObject("STATUS_CODE", Integer.class),
                nullableLong(resultSet, "DURATION_MS"),
                nullableLong(resultSet, "REGION_ID"),
                nullableLong(resultSet, "OUTLET_ID"),
                nullableLong(resultSet, "USER_ID"),
                fromJson(resultSet.getString("PAYLOAD_JSON"))
        );
    }

    private <T> Optional<T> queryOptional(String sql, MapSqlParameterSource params, ResultSetMapper<T> mapper) {
        return Optional.ofNullable(jdbcTemplate.query(sql, params, resultSet -> resultSet.next() ? mapper.map(resultSet) : null));
    }

    private Instant instant(ResultSet resultSet, String columnName) throws SQLException {
        OffsetDateTime offsetDateTime = resultSet.getObject(columnName, OffsetDateTime.class);
        if (offsetDateTime != null) {
            return offsetDateTime.toInstant();
        }
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String fallbackIdempotency(String idempotencyKey, String fallback) {
        if (hasText(idempotencyKey)) {
            return idempotencyKey;
        }
        return fallback;
    }

    private String moduleFromPayload(Map<String, Object> payload, String sourceService) {
        if (payload != null) {
            Object value = payload.get("module");
            if (value instanceof String string && hasText(string)) {
                return string;
            }
        }
        return sourceService;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit payload", exception);
        }
    }

    private Object fromJson(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize audit payload", exception);
        }
    }

    private record QueryParts(String sql, MapSqlParameterSource params) {
    }

    @FunctionalInterface
    private interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
