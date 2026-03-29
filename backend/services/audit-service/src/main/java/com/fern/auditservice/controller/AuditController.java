package com.fern.auditservice.controller;

import com.fern.auditservice.dto.AuditEventDetailResponse;
import com.fern.auditservice.dto.AuditEventSummaryResponse;
import com.fern.auditservice.dto.AuditListResponse;
import com.fern.auditservice.dto.RequestTraceDetailResponse;
import com.fern.auditservice.dto.RequestTraceSummaryResponse;
import com.fern.auditservice.dto.SecurityEventDetailResponse;
import com.fern.auditservice.dto.SecurityEventSummaryResponse;
import com.fern.auditservice.repository.AuditEventFilter;
import com.fern.auditservice.repository.RequestTraceFilter;
import com.fern.auditservice.repository.SecurityEventFilter;
import com.fern.auditservice.service.AuditAuthorizer;
import com.fern.auditservice.service.AuditQueryService;
import com.fern.platform.common.FernPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/audit")
public class AuditController {
    private final AuditAuthorizer auditAuthorizer;
    private final AuditQueryService auditQueryService;

    public AuditController(AuditAuthorizer auditAuthorizer, AuditQueryService auditQueryService) {
        this.auditAuthorizer = auditAuthorizer;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/events")
    public AuditListResponse<AuditEventSummaryResponse> listAuditEvents(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        auditAuthorizer.requireRead(principal);
        return new AuditListResponse<>(auditQueryService.listAuditEvents(principal, new AuditEventFilter(
                userId,
                sourceService,
                module,
                action,
                resourceType,
                resourceId,
                occurredFrom,
                occurredTo,
                regionId,
                outletId,
                outcome,
                correlationId,
                limit
        )));
    }

    @GetMapping("/events/{id}")
    public AuditEventDetailResponse getAuditEvent(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        auditAuthorizer.requireRead(principal);
        return auditQueryService.getAuditEvent(principal, id, auditAuthorizer.canReadDetails(principal));
    }

    @GetMapping("/security-events")
    public AuditListResponse<SecurityEventSummaryResponse> listSecurityEvents(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        auditAuthorizer.requireRead(principal);
        return new AuditListResponse<>(auditQueryService.listSecurityEvents(principal, new SecurityEventFilter(
                userId,
                sourceService,
                module,
                eventType,
                occurredFrom,
                occurredTo,
                outcome,
                correlationId,
                limit
        )));
    }

    @GetMapping("/security-events/{id}")
    public SecurityEventDetailResponse getSecurityEvent(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        auditAuthorizer.requireRead(principal);
        return auditQueryService.getSecurityEvent(principal, id, auditAuthorizer.canReadDetails(principal));
    }

    @GetMapping("/request-traces")
    public AuditListResponse<RequestTraceSummaryResponse> listRequestTraces(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        auditAuthorizer.requireRead(principal);
        return new AuditListResponse<>(auditQueryService.listRequestTraces(principal, new RequestTraceFilter(
                userId,
                sourceService,
                module,
                endpoint,
                method,
                occurredFrom,
                occurredTo,
                regionId,
                outletId,
                statusCode,
                correlationId,
                limit
        )));
    }

    @GetMapping("/request-traces/{id}")
    public RequestTraceDetailResponse getRequestTrace(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        auditAuthorizer.requireRead(principal);
        return auditQueryService.getRequestTrace(principal, id, auditAuthorizer.canReadDetails(principal));
    }
}
