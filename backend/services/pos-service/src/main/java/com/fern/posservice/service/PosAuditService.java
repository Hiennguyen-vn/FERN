package com.fern.posservice.service;

import com.fern.platform.audit.AbstractAuditService;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Centralized audit service for POS operations.
 * Covers AUD-006: cancel order with before/after snapshot.
 */
@Service
public class PosAuditService extends AbstractAuditService {

    public PosAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        super(auditEventPublisher, clock);
    }

    public void publishOrderEvent(
            String eventType,
            FernPrincipal principal,
            Long regionId,
            Long outletId,
            String action,
            Long orderId,
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
                "SALE_ORDER",
                orderId == null ? null : orderId.toString(),
                oldValue,
                newValue,
                payload
        ));
    }

    @Override
    protected String sourceService() {
        return "pos-service";
    }
}
