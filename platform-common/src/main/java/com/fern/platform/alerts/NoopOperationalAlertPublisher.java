package com.fern.platform.alerts;

import java.util.Map;

public class NoopOperationalAlertPublisher implements OperationalAlertPublisher {
    @Override
    public void publish(
            String alertType,
            String severity,
            String summary,
            String correlationId,
            Long regionId,
            Long outletId,
            String entityType,
            String entityId,
            Map<String, Object> details
    ) {
    }
}
