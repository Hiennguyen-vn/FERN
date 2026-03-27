package com.fern.auditservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.auditservice.repository.AuditEventFilter;
import com.fern.auditservice.service.AuditAuthorizer;
import com.fern.auditservice.service.AuditIngestionService;
import com.fern.auditservice.service.AuditQueryService;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AuditServiceIntegrationTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:1");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("fern.id-generator.node-id", () -> "17");
    }

    @Autowired
    private AuditIngestionService auditIngestionService;

    @Autowired
    private AuditQueryService auditQueryService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditAuthorizer auditAuthorizer;

    @Test
    void shouldBootOnPostgresAndSerializeAuditIdsAsStrings() throws Exception {
        doNothing().when(auditAuthorizer).requireRead(null);
        when(auditAuthorizer.canReadDetails(null)).thenReturn(true);

        AuditEvent event = new AuditEvent(
                "audit-int-1",
                "catalog.product.changed",
                Instant.parse("2026-03-27T10:00:00Z"),
                "catalog-service",
                "corr-int-1",
                3L,
                1L,
                2L,
                "UPDATE",
                "PRODUCT",
                "55",
                "SUCCESS",
                Map.of("status", "DRAFT"),
                Map.of("status", "ACTIVE"),
                "idem-audit-int-1",
                Map.of("module", "catalog", "changedBy", "integration")
        );

        auditIngestionService.ingestAuditEvent(event);
        auditIngestionService.ingestAuditEvent(event);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE source_event_id = :sourceEventId",
                Map.of("sourceEventId", "audit-int-1"),
                Integer.class
        );
        assertThat(rowCount).isEqualTo(1);

        var summary = auditQueryService.listAuditEvents(new AuditEventFilter(
                null, null, null, null, null, null, null, null, null, null, null, null, 10
        )).stream()
                .filter(item -> "audit-int-1".equals(item.sourceEventId()))
                .findFirst()
                .orElseThrow();

        String serialized = objectMapper.writeValueAsString(summary);
        assertThat(serialized).contains("\"id\":\"" + summary.id() + "\"");

        mockMvc.perform(get("/audit/events/{id}", summary.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(summary.id().toString()))
                .andExpect(jsonPath("$.payload.module").value("catalog"));
    }

    @Test
    void shouldPersistRequestTracePayloadAsJsonb() {
        RequestTraceEvent event = new RequestTraceEvent(
                "trace-int-1",
                "request.trace.recorded",
                Instant.parse("2026-03-27T10:05:00Z"),
                "api-gateway",
                "corr-trace-1",
                "req-1",
                "/audit/events",
                "GET",
                200,
                12L,
                4L,
                1L,
                2L,
                "idem-trace-int-1",
                Map.of("module", "gateway", "userAgent", "curl/8.7")
        );

        auditIngestionService.ingestRequestTrace(event);

        String payloadType = jdbcTemplate.queryForObject(
                "SELECT pg_typeof(payload)::text FROM audit.request_trace WHERE source_event_id = :sourceEventId",
                Map.of("sourceEventId", "trace-int-1"),
                String.class
        );
        assertThat(payloadType).isEqualTo("jsonb");

        var trace = auditQueryService.listRequestTraces(new com.fern.auditservice.repository.RequestTraceFilter(
                null, null, null, null, null, null, null, null, null, null, null, 10
        )).stream()
                .filter(item -> "trace-int-1".equals(item.sourceEventId()))
                .findFirst()
                .orElseThrow();
        assertThat(trace.id()).isNotNull();
    }
}
