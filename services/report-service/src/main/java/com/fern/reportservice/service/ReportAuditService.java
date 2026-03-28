package com.fern.reportservice.service;

import com.fern.platform.audit.AbstractAuditService;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportAuditService extends AbstractAuditService {

    public ReportAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        super(auditEventPublisher, clock);
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
        publishToOutbox(buildAuditEvent(
                eventType,
                principal,
                correlationId,
                regionId,
                outletId,
                action,
                resourceType,
                resourceId,
                oldValue,
                newValue,
                payload
        ));
    }

    @Override
    protected String sourceService() {
        return "report-service";
    }
}
