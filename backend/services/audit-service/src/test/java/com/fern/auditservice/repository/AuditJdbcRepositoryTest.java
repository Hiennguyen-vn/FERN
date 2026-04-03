package com.fern.auditservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.audit.SecurityEvent;
import com.fern.platform.common.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AuditJdbcRepositoryTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private AuditJdbcRepository auditJdbcRepository;
    private List<String> sqlStatements;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sqlStatements = new ArrayList<>();
        auditJdbcRepository = new AuditJdbcRepository(
                jdbcTemplate,
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules(),
                new SnowflakeIdGenerator(1)
        );

        doAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
        doAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return List.of();
        }).when(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        doAnswer(invocation -> {
            sqlStatements.add(invocation.getArgument(0));
            return null;
        }).when(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class));
    }

    @Test
    void shouldUseConfiguredDefaultSchemaInsteadOfHardcodedQualifiedTableNames() {
        Instant occurredAt = Instant.parse("2026-03-27T12:00:00Z");
        auditJdbcRepository.insertAuditEvent(new AuditEvent(
                "audit-event-1",
                "catalog.product.changed",
                occurredAt,
                "catalog-service",
                "corr-1",
                11L,
                22L,
                33L,
                "UPDATE_PRODUCT",
                "product",
                "5",
                "SUCCESS",
                Map.of("before", "old"),
                Map.of("after", "new"),
                "idem-audit",
                Map.of("module", "catalog")
        ));
        auditJdbcRepository.insertSecurityEvent(new SecurityEvent(
                "security-event-1",
                "iam.auth.login.failed",
                occurredAt,
                "iam-service",
                "corr-2",
                44L,
                "FAILURE",
                "bad_credentials",
                "127.0.0.1",
                "curl/8.0",
                "idem-security",
                Map.of("module", "iam")
        ));
        auditJdbcRepository.insertRequestTrace(new RequestTraceEvent(
                "trace-event-1",
                "request.trace.recorded",
                occurredAt,
                "api-gateway",
                "corr-3",
                "req-1",
                "/audit/events",
                "GET",
                200,
                18L,
                55L,
                66L,
                77L,
                "idem-trace",
                Map.of("module", "gateway")
        ));

        auditJdbcRepository.findAuditEventById(1L);
        auditJdbcRepository.findSecurityEventById(2L);
        auditJdbcRepository.findRequestTraceById(3L);
        auditJdbcRepository.findAuditEvents(new AuditEventFilter(null, null, null, null, null, null, null, null, null, null, null, null, 100));
        auditJdbcRepository.findSecurityEvents(new SecurityEventFilter(null, null, null, null, null, null, null, null, 100));
        auditJdbcRepository.findRequestTraces(new RequestTraceFilter(null, null, null, null, null, null, null, null, null, null, null, 100));

        assertThat(sqlStatements).isNotEmpty();
        assertThat(sqlStatements).allMatch(sql -> !sql.contains("FERN_" + "REPORTING"));
        assertThat(sqlStatements).anyMatch(sql -> sql.contains("INSERT INTO audit.audit_event"));
        assertThat(sqlStatements).anyMatch(sql -> sql.contains("FROM audit.audit_event"));
        assertThat(sqlStatements).anyMatch(sql -> sql.contains("INSERT INTO audit.security_event"));
        assertThat(sqlStatements).anyMatch(sql -> sql.contains("FROM audit.security_event"));
        assertThat(sqlStatements).anyMatch(sql -> sql.contains("INSERT INTO audit.request_trace"));
        assertThat(sqlStatements).anyMatch(sql -> sql.contains("FROM audit.request_trace"));
    }

    @Test
    void shouldPushAccessScopePredicatesIntoAuditAndTraceQueries() {
        auditJdbcRepository.findAuditEvents(
                new AuditEventFilter(null, null, null, null, null, null, null, null, null, null, null, null, 100),
                new AuditAccessScope(false, List.of(10L), List.of(20L))
        );
        auditJdbcRepository.findRequestTraces(
                new RequestTraceFilter(null, null, null, null, null, null, null, null, null, null, null, 100),
                new AuditAccessScope(false, List.of(10L), List.of(20L))
        );

        assertThat(sqlStatements).anyMatch(sql -> sql.contains("(outlet_id IN (:scopeOutlets) OR region_id IN (:scopeRegions))"));
    }
}
