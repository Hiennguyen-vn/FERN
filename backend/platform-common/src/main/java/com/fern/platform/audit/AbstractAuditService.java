package com.fern.platform.audit;

import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractAuditService {
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    protected AbstractAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    protected AuditEvent buildAuditEvent(
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
        return new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                clock.instant(),
                sourceService(),
                correlationId,
                principal == null ? null : principal.userId(),
                regionId,
                outletId,
                action,
                resourceType,
                resourceId,
                "SUCCESS",
                maskValue(oldValue),
                maskValue(newValue),
                UUID.randomUUID().toString(),
                maskSensitiveFields(payload)
        );
    }

    protected Map<String, Object> maskSensitiveFields(Map<String, Object> payload) {
        Object masked = SensitiveDataMasker.mask(mapPayload(payload == null ? Map.of() : payload));
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

    protected void publishToOutbox(AuditEvent event) {
        auditEventPublisher.publishAuditEvent(event);
    }

    protected Object maskValue(Object value) {
        return SensitiveDataMasker.mask(value);
    }

    protected Map<String, Object> mapPayload(Map<String, Object> payload) {
        return payload;
    }

    protected abstract String sourceService();
}
