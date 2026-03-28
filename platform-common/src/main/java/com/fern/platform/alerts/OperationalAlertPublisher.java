package com.fern.platform.alerts;

import java.util.Map;

public interface OperationalAlertPublisher {
    void publish(
            String alertType,
            String severity,
            String summary,
            String correlationId,
            Long regionId,
            Long outletId,
            String entityType,
            String entityId,
            Map<String, Object> details
    );
}
