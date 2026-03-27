package com.fern.auditservice.messaging;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.auditservice.service.AuditIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuditEventConsumerTest {
    @Mock
    private AuditIngestionService auditIngestionService;

    private AuditEventConsumer auditEventConsumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditEventConsumer = new AuditEventConsumer(new ObjectMapper().findAndRegisterModules(), auditIngestionService);
    }

    @Test
    void shouldConsumeAuditEvent() {
        auditEventConsumer.consumeAuditEvent("""
                {
                  "eventId":"evt-1",
                  "eventType":"catalog.product.changed",
                  "occurredAt":"2026-03-27T10:00:00Z",
                  "sourceService":"catalog-service",
                  "correlationId":"corr-1",
                  "userId":1,
                  "regionId":1,
                  "outletId":2,
                  "action":"UPDATE",
                  "resourceType":"PRODUCT",
                  "resourceId":"5",
                  "outcome":"SUCCESS",
                  "oldValue":{"status":"DRAFT"},
                  "newValue":{"status":"ACTIVE"},
                  "idempotencyKey":"idem-1",
                  "payload":{"module":"catalog"}
                }
                """);

        verify(auditIngestionService).ingestAuditEvent(argThat(event ->
                event.eventId().equals("evt-1") && event.action().equals("UPDATE") && event.resourceId().equals("5")));
    }

    @Test
    void shouldConsumeSecurityEvent() {
        auditEventConsumer.consumeSecurityEvent("""
                {
                  "eventId":"evt-2",
                  "eventType":"auth.login.failed",
                  "occurredAt":"2026-03-27T10:00:00Z",
                  "sourceService":"iam-service",
                  "correlationId":"corr-2",
                  "userId":2,
                  "outcome":"FAILURE",
                  "failureReason":"bad_credentials",
                  "ipAddress":"127.0.0.1",
                  "userAgent":"curl/8.0",
                  "idempotencyKey":"idem-2",
                  "payload":{"module":"iam"}
                }
                """);

        verify(auditIngestionService).ingestSecurityEvent(argThat(event ->
                event.eventId().equals("evt-2") && event.failureReason().equals("bad_credentials")));
    }

    @Test
    void shouldConsumeRequestTrace() {
        auditEventConsumer.consumeRequestTrace("""
                {
                  "eventId":"evt-3",
                  "eventType":"request.trace.recorded",
                  "occurredAt":"2026-03-27T10:00:00Z",
                  "sourceService":"api-gateway",
                  "correlationId":"corr-3",
                  "requestId":"req-1",
                  "endpoint":"/audit/events",
                  "method":"GET",
                  "statusCode":200,
                  "durationMs":15,
                  "userId":3,
                  "regionId":1,
                  "outletId":2,
                  "idempotencyKey":"idem-3",
                  "payload":{"module":"gateway"}
                }
                """);

        verify(auditIngestionService).ingestRequestTrace(argThat(event ->
                event.eventId().equals("evt-3") && event.endpoint().equals("/audit/events") && event.statusCode() == 200));
    }
}
