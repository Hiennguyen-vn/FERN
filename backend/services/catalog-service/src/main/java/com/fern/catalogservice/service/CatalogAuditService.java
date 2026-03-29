package com.fern.catalogservice.service;

import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.SensitiveDataMasker;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class CatalogAuditService {
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public CatalogAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public void publish(
            String eventType,
            FernPrincipal principal,
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
                "catalog-service",
                correlationId(),
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
        return masked instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private String correlationId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getHeader(CorrelationId.HEADER);
    }
}
