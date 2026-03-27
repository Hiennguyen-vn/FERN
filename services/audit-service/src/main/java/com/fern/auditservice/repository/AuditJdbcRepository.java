package com.fern.auditservice.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.audit.SecurityEvent;
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
    private static final String AUDIT_EVENT_TABLE = "FERN_REPORTING.AUDIT.AUDIT_EVENT";
    private static final String SECURITY_EVENT_TABLE = "FERN_REPORTING.AUDIT.SECURITY_EVENT";
    private static final String REQUEST_TRACE_TABLE = "FERN_REPORTING.AUDIT.REQUEST_TRACE";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insertAuditEvent(AuditEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceEventId", event.eventId())
                .addValue("sourceService", event.sourceService())
                .addValue("module", moduleFromPayload(event.payload(), event.sourceService()))
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", event.occurredAt())
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
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    REGION_ID,
                    OUTLET_ID,
                    USER_ID,
                    ACTION,
                    RESOURCE_TYPE,
                    RESOURCE_ID,
                    OUTCOME,
                    OLD_VALUE,
                    NEW_VALUE,
                    PAYLOAD
                )
                SELECT
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
                    IFF(:oldValueJson IS NULL, NULL, PARSE_JSON(:oldValueJson)),
                    IFF(:newValueJson IS NULL, NULL, PARSE_JSON(:newValueJson)),
                    IFF(:payloadJson IS NULL, NULL, PARSE_JSON(:payloadJson))
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM %s
                    WHERE SOURCE_EVENT_ID = :sourceEventId
                       OR IDEMPOTENCY_KEY = :idempotencyKey
                )
                """.formatted(AUDIT_EVENT_TABLE, AUDIT_EVENT_TABLE), params);
    }

    public void insertSecurityEvent(SecurityEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceEventId", event.eventId())
                .addValue("sourceService", event.sourceService())
                .addValue("module", moduleFromPayload(event.payload(), event.sourceService()))
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", event.occurredAt())
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
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    USER_ID,
                    OUTCOME,
                    FAILURE_REASON,
                    IP_ADDRESS,
                    USER_AGENT,
                    PAYLOAD
                )
                SELECT
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
                    IFF(:payloadJson IS NULL, NULL, PARSE_JSON(:payloadJson))
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM %s
                    WHERE SOURCE_EVENT_ID = :sourceEventId
                       OR IDEMPOTENCY_KEY = :idempotencyKey
                )
                """.formatted(SECURITY_EVENT_TABLE, SECURITY_EVENT_TABLE), params);
    }

    public void insertRequestTrace(RequestTraceEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceEventId", event.eventId())
                .addValue("sourceService", event.sourceService())
                .addValue("module", moduleFromPayload(event.payload(), event.sourceService()))
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", event.occurredAt())
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
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    REQUEST_ID,
                    ENDPOINT,
                    METHOD,
                    STATUS_CODE,
                    DURATION_MS,
                    REGION_ID,
                    OUTLET_ID,
                    USER_ID,
                    PAYLOAD
                )
                SELECT
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
                    IFF(:payloadJson IS NULL, NULL, PARSE_JSON(:payloadJson))
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM %s
                    WHERE SOURCE_EVENT_ID = :sourceEventId
                       OR IDEMPOTENCY_KEY = :idempotencyKey
                )
                """.formatted(REQUEST_TRACE_TABLE, REQUEST_TRACE_TABLE), params);
    }

    public List<AuditEventRow> findAuditEvents(AuditEventFilter filter) {
        QueryParts query = auditEventQuery(filter);
        return jdbcTemplate.query(query.sql(), query.params(), (resultSet, rowNum) -> mapAuditEvent(resultSet));
    }

    public Optional<AuditEventRow> findAuditEventById(Long id) {
        return queryOptional("""
                SELECT
                    AUDIT_EVENT_ID,
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    COALESCE(MODULE, SOURCE_SERVICE) AS MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    INGESTED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    REGION_ID,
                    OUTLET_ID,
                    USER_ID,
                    ACTION,
                    RESOURCE_TYPE,
                    RESOURCE_ID,
                    OUTCOME,
                    TO_JSON(OLD_VALUE) AS OLD_VALUE_JSON,
                    TO_JSON(NEW_VALUE) AS NEW_VALUE_JSON,
                    TO_JSON(PAYLOAD) AS PAYLOAD_JSON
                FROM %s
                WHERE AUDIT_EVENT_ID = :id
                """.formatted(AUDIT_EVENT_TABLE), new MapSqlParameterSource("id", id), this::mapAuditEvent);
    }

    public List<SecurityEventRow> findSecurityEvents(SecurityEventFilter filter) {
        QueryParts query = securityEventQuery(filter);
        return jdbcTemplate.query(query.sql(), query.params(), (resultSet, rowNum) -> mapSecurityEvent(resultSet));
    }

    public Optional<SecurityEventRow> findSecurityEventById(Long id) {
        return queryOptional("""
                SELECT
                    SECURITY_EVENT_ID,
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    COALESCE(MODULE, SOURCE_SERVICE) AS MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    INGESTED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    USER_ID,
                    OUTCOME,
                    FAILURE_REASON,
                    IP_ADDRESS,
                    USER_AGENT,
                    TO_JSON(PAYLOAD) AS PAYLOAD_JSON
                FROM %s
                WHERE SECURITY_EVENT_ID = :id
                """.formatted(SECURITY_EVENT_TABLE), new MapSqlParameterSource("id", id), this::mapSecurityEvent);
    }

    public List<RequestTraceRow> findRequestTraces(RequestTraceFilter filter) {
        QueryParts query = requestTraceQuery(filter);
        return jdbcTemplate.query(query.sql(), query.params(), (resultSet, rowNum) -> mapRequestTrace(resultSet));
    }

    public Optional<RequestTraceRow> findRequestTraceById(Long id) {
        return queryOptional("""
                SELECT
                    REQUEST_TRACE_ID,
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    COALESCE(MODULE, SOURCE_SERVICE) AS MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    INGESTED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    REQUEST_ID,
                    ENDPOINT,
                    METHOD,
                    STATUS_CODE,
                    DURATION_MS,
                    REGION_ID,
                    OUTLET_ID,
                    USER_ID,
                    TO_JSON(PAYLOAD) AS PAYLOAD_JSON
                FROM %s
                WHERE REQUEST_TRACE_ID = :id
                """.formatted(REQUEST_TRACE_TABLE), new MapSqlParameterSource("id", id), this::mapRequestTrace);
    }

    private QueryParts auditEventQuery(AuditEventFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", sanitizeLimit(filter.limit()));
        addCommonConditions(conditions, params, filter.userId(), filter.sourceService(), filter.module(), filter.occurredFrom(), filter.occurredTo(), filter.regionId(), filter.outletId(), filter.correlationId());
        if (hasText(filter.action())) {
            conditions.add("ACTION = :action");
            params.addValue("action", filter.action());
        }
        if (hasText(filter.resourceType())) {
            conditions.add("RESOURCE_TYPE = :resourceType");
            params.addValue("resourceType", filter.resourceType());
        }
        if (hasText(filter.resourceId())) {
            conditions.add("RESOURCE_ID = :resourceId");
            params.addValue("resourceId", filter.resourceId());
        }
        if (hasText(filter.outcome())) {
            conditions.add("OUTCOME = :outcome");
            params.addValue("outcome", filter.outcome());
        }

        return new QueryParts("""
                SELECT
                    AUDIT_EVENT_ID,
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    COALESCE(MODULE, SOURCE_SERVICE) AS MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    INGESTED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    REGION_ID,
                    OUTLET_ID,
                    USER_ID,
                    ACTION,
                    RESOURCE_TYPE,
                    RESOURCE_ID,
                    OUTCOME,
                    TO_JSON(OLD_VALUE) AS OLD_VALUE_JSON,
                    TO_JSON(NEW_VALUE) AS NEW_VALUE_JSON,
                    TO_JSON(PAYLOAD) AS PAYLOAD_JSON
                FROM %s
                %s
                ORDER BY OCCURRED_AT DESC, AUDIT_EVENT_ID DESC
                LIMIT :limit
                """.formatted(AUDIT_EVENT_TABLE, whereClause(conditions)), params);
    }

    private QueryParts securityEventQuery(SecurityEventFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", sanitizeLimit(filter.limit()));
        addCommonConditions(conditions, params, filter.userId(), filter.sourceService(), filter.module(), filter.occurredFrom(), filter.occurredTo(), null, null, filter.correlationId());
        if (hasText(filter.eventType())) {
            conditions.add("EVENT_TYPE = :eventType");
            params.addValue("eventType", filter.eventType());
        }
        if (hasText(filter.outcome())) {
            conditions.add("OUTCOME = :outcome");
            params.addValue("outcome", filter.outcome());
        }

        return new QueryParts("""
                SELECT
                    SECURITY_EVENT_ID,
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    COALESCE(MODULE, SOURCE_SERVICE) AS MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    INGESTED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    USER_ID,
                    OUTCOME,
                    FAILURE_REASON,
                    IP_ADDRESS,
                    USER_AGENT,
                    TO_JSON(PAYLOAD) AS PAYLOAD_JSON
                FROM %s
                %s
                ORDER BY OCCURRED_AT DESC, SECURITY_EVENT_ID DESC
                LIMIT :limit
                """.formatted(SECURITY_EVENT_TABLE, whereClause(conditions)), params);
    }

    private QueryParts requestTraceQuery(RequestTraceFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", sanitizeLimit(filter.limit()));
        addCommonConditions(conditions, params, filter.userId(), filter.sourceService(), filter.module(), filter.occurredFrom(), filter.occurredTo(), filter.regionId(), filter.outletId(), filter.correlationId());
        if (hasText(filter.endpoint())) {
            conditions.add("ENDPOINT = :endpoint");
            params.addValue("endpoint", filter.endpoint());
        }
        if (hasText(filter.method())) {
            conditions.add("METHOD = :method");
            params.addValue("method", filter.method());
        }
        if (filter.statusCode() != null) {
            conditions.add("STATUS_CODE = :statusCode");
            params.addValue("statusCode", filter.statusCode());
        }

        return new QueryParts("""
                SELECT
                    REQUEST_TRACE_ID,
                    SOURCE_EVENT_ID,
                    SOURCE_SERVICE,
                    COALESCE(MODULE, SOURCE_SERVICE) AS MODULE,
                    EVENT_TYPE,
                    OCCURRED_AT,
                    INGESTED_AT,
                    IDEMPOTENCY_KEY,
                    CORRELATION_ID,
                    REQUEST_ID,
                    ENDPOINT,
                    METHOD,
                    STATUS_CODE,
                    DURATION_MS,
                    REGION_ID,
                    OUTLET_ID,
                    USER_ID,
                    TO_JSON(PAYLOAD) AS PAYLOAD_JSON
                FROM %s
                %s
                ORDER BY OCCURRED_AT DESC, REQUEST_TRACE_ID DESC
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
            conditions.add("USER_ID = :userId");
            params.addValue("userId", userId);
        }
        if (hasText(sourceService)) {
            conditions.add("SOURCE_SERVICE = :sourceService");
            params.addValue("sourceService", sourceService);
        }
        if (hasText(module)) {
            conditions.add("COALESCE(MODULE, SOURCE_SERVICE) = :module");
            params.addValue("module", module);
        }
        if (occurredFrom != null) {
            conditions.add("OCCURRED_AT >= :occurredFrom");
            params.addValue("occurredFrom", occurredFrom);
        }
        if (occurredTo != null) {
            conditions.add("OCCURRED_AT <= :occurredTo");
            params.addValue("occurredTo", occurredTo);
        }
        if (regionId != null) {
            conditions.add("REGION_ID = :regionId");
            params.addValue("regionId", regionId);
        }
        if (outletId != null) {
            conditions.add("OUTLET_ID = :outletId");
            params.addValue("outletId", outletId);
        }
        if (hasText(correlationId)) {
            conditions.add("CORRELATION_ID = :correlationId");
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
