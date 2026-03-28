package com.fern.reportservice.service;

import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.SensitiveDataMasker;
import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReportAuditService {
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public ReportAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public void publish(
            String eventType,
            FernPrincipal principal,
            String correlationId,
            Long regionId,
            Long outletId,
            String action,
            String resourceType,
            String resourceId,
            Object oldValue,
            Object newValue,
            Map<String, Object> payload
    ) {
        auditEventPublisher.publishAuditEvent(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                clock.instant(),
                "report-service",
                correlationId,
                principal == null ? null : principal.userId(),
                regionId,
                outletId,
                action,
                resourceType,
                resourceId,
                "SUCCESS",
                SensitiveDataMasker.mask(oldValue),
                SensitiveDataMasker.mask(newValue),
                UUID.randomUUID().toString(),
                mask(payload)
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mask(Map<String, Object> payload) {
        Object masked = SensitiveDataMasker.mask(payload == null ? Map.of() : payload);
        if (!(masked instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key instanceof String stringKey && value != null) {
                sanitized.put(stringKey, value);
            }
        });
        return sanitized;
    }
}
