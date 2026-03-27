package com.fern.auditservice.service;

import com.fern.auditservice.dto.AuditEventDetailResponse;
import com.fern.auditservice.dto.AuditEventSummaryResponse;
import com.fern.auditservice.dto.RequestTraceDetailResponse;
import com.fern.auditservice.dto.RequestTraceSummaryResponse;
import com.fern.auditservice.dto.SecurityEventDetailResponse;
import com.fern.auditservice.dto.SecurityEventSummaryResponse;
import com.fern.auditservice.repository.AuditEventFilter;
import com.fern.auditservice.repository.AuditEventRow;
import com.fern.auditservice.repository.AuditJdbcRepository;
import com.fern.auditservice.repository.RequestTraceFilter;
import com.fern.auditservice.repository.RequestTraceRow;
import com.fern.auditservice.repository.SecurityEventFilter;
import com.fern.auditservice.repository.SecurityEventRow;
import com.fern.platform.audit.SensitiveDataMasker;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
    private final AuditJdbcRepository auditJdbcRepository;

    public AuditQueryService(AuditJdbcRepository auditJdbcRepository) {
        this.auditJdbcRepository = auditJdbcRepository;
    }

    public List<AuditEventSummaryResponse> listAuditEvents(FernPrincipal principal, AuditEventFilter filter) {
        return listAuditEvents(filter).stream()
                .filter(item -> canAccess(principal, item.regionId(), item.outletId()))
                .toList();
    }

    public List<AuditEventSummaryResponse> listAuditEvents(AuditEventFilter filter) {
        return auditJdbcRepository.findAuditEvents(filter).stream()
                .map(this::toAuditSummary)
                .toList();
    }

    public AuditEventDetailResponse getAuditEvent(FernPrincipal principal, Long id, boolean includeDetails) {
        AuditEventRow row = auditJdbcRepository.findAuditEventById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event not found"));
        if (!canAccess(principal, row.regionId(), row.outletId())) {
            throw new ResourceNotFoundException("Audit event not found");
        }
        return toAuditDetail(row, includeDetails);
    }

    public AuditEventDetailResponse getAuditEvent(Long id, boolean includeDetails) {
        AuditEventRow row = auditJdbcRepository.findAuditEventById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit event not found"));
        return toAuditDetail(row, includeDetails);
    }

    public List<SecurityEventSummaryResponse> listSecurityEvents(FernPrincipal principal, SecurityEventFilter filter) {
        return listSecurityEvents(filter).stream()
                .filter(item -> canAccessGlobal(principal))
                .toList();
    }

    public List<SecurityEventSummaryResponse> listSecurityEvents(SecurityEventFilter filter) {
        return auditJdbcRepository.findSecurityEvents(filter).stream()
                .map(this::toSecuritySummary)
                .toList();
    }

    public SecurityEventDetailResponse getSecurityEvent(FernPrincipal principal, Long id, boolean includeDetails) {
        SecurityEventRow row = auditJdbcRepository.findSecurityEventById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Security event not found"));
        if (!canAccessGlobal(principal)) {
            throw new ResourceNotFoundException("Security event not found");
        }
        return toSecurityDetail(row, includeDetails);
    }

    public SecurityEventDetailResponse getSecurityEvent(Long id, boolean includeDetails) {
        SecurityEventRow row = auditJdbcRepository.findSecurityEventById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Security event not found"));
        return toSecurityDetail(row, includeDetails);
    }

    public List<RequestTraceSummaryResponse> listRequestTraces(FernPrincipal principal, RequestTraceFilter filter) {
        return listRequestTraces(filter).stream()
                .filter(item -> canAccess(principal, item.regionId(), item.outletId()))
                .toList();
    }

    public List<RequestTraceSummaryResponse> listRequestTraces(RequestTraceFilter filter) {
        return auditJdbcRepository.findRequestTraces(filter).stream()
                .map(this::toRequestTraceSummary)
                .toList();
    }

    public RequestTraceDetailResponse getRequestTrace(FernPrincipal principal, Long id, boolean includeDetails) {
        RequestTraceRow row = auditJdbcRepository.findRequestTraceById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request trace not found"));
        if (!canAccess(principal, row.regionId(), row.outletId())) {
            throw new ResourceNotFoundException("Request trace not found");
        }
        return toRequestTraceDetail(row, includeDetails);
    }

    public RequestTraceDetailResponse getRequestTrace(Long id, boolean includeDetails) {
        RequestTraceRow row = auditJdbcRepository.findRequestTraceById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request trace not found"));
        return toRequestTraceDetail(row, includeDetails);
    }

    private AuditEventSummaryResponse toAuditSummary(AuditEventRow row) {
        return new AuditEventSummaryResponse(
                row.id(),
                row.sourceEventId(),
                row.sourceService(),
                row.module(),
                row.eventType(),
                row.occurredAt(),
                row.ingestedAt(),
                row.correlationId(),
                row.regionId(),
                row.outletId(),
                row.userId(),
                row.action(),
                row.resourceType(),
                row.resourceId(),
                row.outcome(),
                auditSummary(row)
        );
    }

    private AuditEventDetailResponse toAuditDetail(AuditEventRow row, boolean includeDetails) {
        boolean detailMasked = !includeDetails;
        return new AuditEventDetailResponse(
                row.id(),
                row.sourceEventId(),
                row.sourceService(),
                row.module(),
                row.eventType(),
                row.occurredAt(),
                row.ingestedAt(),
                row.idempotencyKey(),
                row.correlationId(),
                row.regionId(),
                row.outletId(),
                row.userId(),
                row.action(),
                row.resourceType(),
                row.resourceId(),
                row.outcome(),
                detailMasked ? null : row.oldValue(),
                detailMasked ? null : row.newValue(),
                detailMasked ? null : row.payload(),
                detailMasked,
                auditSummary(row)
        );
    }

    private SecurityEventSummaryResponse toSecuritySummary(SecurityEventRow row) {
        return new SecurityEventSummaryResponse(
                row.id(),
                row.sourceEventId(),
                row.sourceService(),
                row.module(),
                row.eventType(),
                row.occurredAt(),
                row.ingestedAt(),
                row.correlationId(),
                row.userId(),
                row.outcome(),
                row.failureReason(),
                securitySummary(row)
        );
    }

    private SecurityEventDetailResponse toSecurityDetail(SecurityEventRow row, boolean includeDetails) {
        boolean detailMasked = !includeDetails;
        return new SecurityEventDetailResponse(
                row.id(),
                row.sourceEventId(),
                row.sourceService(),
                row.module(),
                row.eventType(),
                row.occurredAt(),
                row.ingestedAt(),
                row.idempotencyKey(),
                row.correlationId(),
                row.userId(),
                row.outcome(),
                row.failureReason(),
                detailMasked ? null : row.ipAddress(),
                detailMasked ? null : row.userAgent(),
                detailMasked ? null : row.payload(),
                detailMasked,
                securitySummary(row)
        );
    }

    private RequestTraceSummaryResponse toRequestTraceSummary(RequestTraceRow row) {
        return new RequestTraceSummaryResponse(
                row.id(),
                row.sourceEventId(),
                row.sourceService(),
                row.module(),
                row.eventType(),
                row.occurredAt(),
                row.ingestedAt(),
                row.correlationId(),
                row.requestId(),
                row.endpoint(),
                row.method(),
                row.statusCode(),
                row.durationMs(),
                row.regionId(),
                row.outletId(),
                row.userId(),
                requestTraceSummary(row)
        );
    }

    private RequestTraceDetailResponse toRequestTraceDetail(RequestTraceRow row, boolean includeDetails) {
        boolean detailMasked = !includeDetails;
        return new RequestTraceDetailResponse(
                row.id(),
                row.sourceEventId(),
                row.sourceService(),
                row.module(),
                row.eventType(),
                row.occurredAt(),
                row.ingestedAt(),
                row.idempotencyKey(),
                row.correlationId(),
                row.requestId(),
                row.endpoint(),
                row.method(),
                row.statusCode(),
                row.durationMs(),
                row.regionId(),
                row.outletId(),
                row.userId(),
                detailMasked ? null : SensitiveDataMasker.mask(row.payload()),
                detailMasked,
                requestTraceSummary(row)
        );
    }

    private String auditSummary(AuditEventRow row) {
        return joinParts(List.of(
                row.action() == null ? row.eventType() : row.action(),
                row.resourceType(),
                row.resourceId(),
                row.outcome()
        ));
    }

    private String securitySummary(SecurityEventRow row) {
        return joinParts(List.of(row.eventType(), row.outcome(), row.failureReason()));
    }

    private String requestTraceSummary(RequestTraceRow row) {
        return joinParts(List.of(row.method(), row.endpoint(), row.statusCode() == null ? null : String.valueOf(row.statusCode())));
    }

    private String joinParts(List<String> parts) {
        return parts.stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private boolean canAccess(FernPrincipal principal, Long regionId, Long outletId) {
        if (principal == null || principal.scopeRoots() == null) {
            return false;
        }
        if (principal.scopeRoots().system()) {
            return true;
        }
        if (outletId != null && principal.scopeRoots().outlets().contains(outletId)) {
            return true;
        }
        return regionId != null && principal.scopeRoots().regions().contains(regionId);
    }

    private boolean canAccessGlobal(FernPrincipal principal) {
        return principal != null && principal.scopeRoots() != null && principal.scopeRoots().system();
    }
}
