package com.fern.auditservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fern.auditservice.dto.AuditEventDetailResponse;
import com.fern.auditservice.dto.RequestTraceDetailResponse;
import com.fern.auditservice.dto.SecurityEventDetailResponse;
import com.fern.auditservice.repository.AuditAccessScope;
import com.fern.auditservice.repository.AuditEventFilter;
import com.fern.auditservice.repository.AuditEventRow;
import com.fern.auditservice.repository.AuditJdbcRepository;
import com.fern.auditservice.repository.RequestTraceFilter;
import com.fern.auditservice.repository.RequestTraceRow;
import com.fern.auditservice.repository.SecurityEventFilter;
import com.fern.auditservice.repository.SecurityEventRow;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.ScopeRoots;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuditQueryServiceTest {
    @Mock
    private AuditJdbcRepository auditJdbcRepository;

    private AuditQueryService auditQueryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditQueryService = new AuditQueryService(auditJdbcRepository);
    }

    @Test
    void shouldMaskAuditDetailsWhenPermissionMissing() {
        AuditEventRow row = new AuditEventRow(
                10L,
                "event-1",
                "iam-service",
                "iam-service",
                "iam.user.created",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-1",
                "corr-1",
                1L,
                2L,
                3L,
                "CREATE",
                "USER",
                "3",
                "SUCCESS",
                Map.of("before", "x"),
                Map.of("after", "y"),
                Map.of("username", "cashier-1")
        );
        when(auditJdbcRepository.findAuditEventById(10L)).thenReturn(Optional.of(row));

        AuditEventDetailResponse response = auditQueryService.getAuditEvent(10L, false);

        assertThat(response.detailMasked()).isTrue();
        assertThat(response.oldValue()).isNull();
        assertThat(response.newValue()).isNull();
        assertThat(response.payload()).isNull();
        assertThat(response.detailSummary()).contains("CREATE");
    }

    @Test
    void shouldExposeAuditDetailsWhenPermissionPresent() {
        AuditEventRow row = new AuditEventRow(
                10L,
                "event-1",
                "catalog-service",
                "catalog",
                "catalog.price.published",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-1",
                "corr-1",
                null,
                null,
                3L,
                "PUBLISH",
                "PRICE",
                "55",
                "SUCCESS",
                Map.of("old", 1),
                Map.of("new", 2),
                Map.of("scope", "OUTLET")
        );
        when(auditJdbcRepository.findAuditEventById(10L)).thenReturn(Optional.of(row));

        AuditEventDetailResponse response = auditQueryService.getAuditEvent(10L, true);

        assertThat(response.detailMasked()).isFalse();
        assertThat(response.oldValue()).isEqualTo(Map.of("old", 1));
        assertThat(response.payload()).isEqualTo(Map.of("scope", "OUTLET"));
    }

    @Test
    void shouldMaskSensitiveSecurityAndTraceDetailsWhenPermissionMissing() {
        SecurityEventRow securityRow = new SecurityEventRow(
                11L,
                "event-2",
                "iam-service",
                "iam-service",
                "auth.login.failed",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-2",
                "corr-2",
                3L,
                "FAILURE",
                "bad_credentials",
                "127.0.0.1",
                "curl/8.0",
                Map.of("attempts", 5)
        );
        RequestTraceRow traceRow = new RequestTraceRow(
                12L,
                "event-3",
                "api-gateway",
                "api-gateway",
                "request.trace.recorded",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-3",
                "corr-3",
                "req-1",
                "/audit/events",
                "GET",
                200,
                14L,
                1L,
                2L,
                3L,
                Map.of("userAgent", "curl/8.0")
        );
        when(auditJdbcRepository.findSecurityEventById(11L)).thenReturn(Optional.of(securityRow));
        when(auditJdbcRepository.findRequestTraceById(12L)).thenReturn(Optional.of(traceRow));

        SecurityEventDetailResponse securityResponse = auditQueryService.getSecurityEvent(11L, false);
        RequestTraceDetailResponse traceResponse = auditQueryService.getRequestTrace(12L, false);

        assertThat(securityResponse.ipAddress()).isNull();
        assertThat(securityResponse.userAgent()).isNull();
        assertThat(securityResponse.payload()).isNull();
        assertThat(traceResponse.payload()).isNull();
    }

    @Test
    void shouldMapListSummaries() {
        AuditEventFilter filter = new AuditEventFilter(null, null, null, null, null, null, null, null, null, null, null, null, 100);
        AuditEventRow row = new AuditEventRow(
                10L,
                "event-1",
                "catalog-service",
                "catalog",
                "catalog.product.changed",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-1",
                "corr-1",
                1L,
                2L,
                3L,
                "UPDATE",
                "PRODUCT",
                "5",
                "SUCCESS",
                null,
                null,
                Map.of()
        );
        when(auditJdbcRepository.findAuditEvents(filter))
                .thenReturn(List.of(row));

        var response = auditQueryService.listAuditEvents(filter);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().detailSummary()).contains("UPDATE");
    }

    @Test
    void shouldApplyPrincipalScopeInAuditEventQuery() {
        FernPrincipal principal = new FernPrincipal(
                7L,
                "regional-ops",
                java.util.Set.of("regional_manager"),
                java.util.Set.of("audit.read"),
                new ScopeRoots(List.of(1L), List.of(2L)),
                1L,
                1L,
                "audit-jti-1"
        );
        AuditEventFilter filter = new AuditEventFilter(null, null, null, null, null, null, null, null, null, null, null, null, 100);
        AuditEventRow row = new AuditEventRow(
                10L,
                "event-1",
                "catalog-service",
                "catalog",
                "catalog.product.changed",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-1",
                "corr-1",
                1L,
                2L,
                3L,
                "UPDATE",
                "PRODUCT",
                "5",
                "SUCCESS",
                null,
                null,
                Map.of()
        );
        when(auditJdbcRepository.findAuditEvents(filter, new AuditAccessScope(false, List.of(1L), List.of(2L))))
                .thenReturn(List.of(row));

        var response = auditQueryService.listAuditEvents(principal, filter);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().regionId()).isEqualTo(1L);
    }

    @Test
    void shouldApplyPrincipalScopeInRequestTraceQuery() {
        FernPrincipal principal = new FernPrincipal(
                8L,
                "regional-auditor",
                java.util.Set.of("regional_auditor"),
                java.util.Set.of("audit.read"),
                new ScopeRoots(List.of(1L), List.of()),
                1L,
                1L,
                "audit-jti-2"
        );
        RequestTraceFilter filter = new RequestTraceFilter(null, null, null, null, null, null, null, null, null, null, null, 100);
        RequestTraceRow row = new RequestTraceRow(
                12L,
                "trace-1",
                "api-gateway",
                "api-gateway",
                "request.trace.recorded",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-3",
                "corr-3",
                "req-1",
                "/audit/events",
                "GET",
                200,
                14L,
                1L,
                20L,
                3L,
                Map.of("userAgent", "curl/8.0")
        );
        when(auditJdbcRepository.findRequestTraces(filter, new AuditAccessScope(false, List.of(1L), List.of())))
                .thenReturn(List.of(row));

        var response = auditQueryService.listRequestTraces(principal, filter);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().regionId()).isEqualTo(1L);
    }

    @Test
    void shouldShortCircuitSecurityEventsForNonSystemPrincipal() {
        FernPrincipal principal = new FernPrincipal(
                9L,
                "regional-auditor",
                java.util.Set.of("regional_auditor"),
                java.util.Set.of("audit.read"),
                new ScopeRoots(List.of(1L), List.of()),
                1L,
                1L,
                "audit-jti-3"
        );

        var response = auditQueryService.listSecurityEvents(principal, new SecurityEventFilter(null, null, null, null, null, null, null, null, 100));

        assertThat(response).isEmpty();
    }

    @Test
    void shouldThrowWhenRecordMissing() {
        when(auditJdbcRepository.findAuditEventById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditQueryService.getAuditEvent(99L, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
