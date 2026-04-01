package com.fern.inventoryservice.service;

import com.fern.platform.audit.AbstractAuditService;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Centralized audit service for inventory operations.
 * Covers AUD-002: inventory waste, adjustment, stock-count operations.
 */
@Service
public class InventoryAuditService extends AbstractAuditService {

    public InventoryAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        super(auditEventPublisher, clock);
    }

    public void publishInventoryEvent(
            String eventType,
            FernPrincipal principal,
            Long regionId,
            Long outletId,
            String action,
            String resourceType,
            Long resourceId,
            Object oldValue,
            Object newValue,
            Map<String, Object> payload
    ) {
        publishToOutbox(buildAuditEvent(
                eventType,
                principal,
                null,
                regionId,
                outletId,
                action,
                resourceType,
                resourceId == null ? null : resourceId.toString(),
                oldValue,
                newValue,
                payload
        ));
    }

    @Override
    protected String sourceService() {
        return "inventory-service";
    }
}
