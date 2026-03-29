package com.fern.platform.audit;

public interface AuditEventPublisher {
    default void publishAuditEvent(AuditEvent event) {
    }

    default void publishSecurityEvent(SecurityEvent event) {
    }

    default void publishRequestTrace(RequestTraceEvent event) {
    }
}
