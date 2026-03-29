package com.fern.auditservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.auditservice.dto.AuditEventDetailResponse;
import com.fern.auditservice.dto.AuditEventSummaryResponse;
import com.fern.auditservice.service.AuditAuthorizer;
import com.fern.auditservice.service.AuditQueryService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditControllerTest {
    @Mock
    private AuditAuthorizer auditAuthorizer;

    @Mock
    private AuditQueryService auditQueryService;

    private MockMvc mockMvc;

    private FernPrincipal principal;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new com.fern.auditservice.controller.AuditController(auditAuthorizer, auditQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        principal = new FernPrincipal(1L, "bootstrap-admin", Set.of("bootstrap_admin"), Set.of("audit.read"), new ScopeRoots(List.of(1L), List.of()), 1L, 1L, "jti");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBindAuditEventFiltersFromQueryString() throws Exception {
        doNothing().when(auditAuthorizer).requireRead(principal);
        when(auditQueryService.listAuditEvents(eq(principal), any(com.fern.auditservice.repository.AuditEventFilter.class)))
                .thenReturn(List.of(new AuditEventSummaryResponse(
                1L,
                "evt-1",
                "catalog-service",
                "catalog",
                "catalog.product.changed",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "corr-1",
                1L,
                2L,
                3L,
                "UPDATE",
                "PRODUCT",
                "5",
                "SUCCESS",
                "UPDATE | PRODUCT | 5 | SUCCESS"
                )));

        authenticate(principal);
        mockMvc.perform(get("/audit/events")
                        .param("sourceService", "catalog-service")
                        .param("action", "UPDATE")
                        .param("resourceType", "PRODUCT")
                        .param("resourceId", "5")
                        .param("correlationId", "corr-1")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("1"))
                .andExpect(jsonPath("$.items[0].sourceService").value("catalog-service"));

        ArgumentCaptor<com.fern.auditservice.repository.AuditEventFilter> captor =
                ArgumentCaptor.forClass(com.fern.auditservice.repository.AuditEventFilter.class);
        verify(auditQueryService).listAuditEvents(eq(principal), captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(25);
        assertThat(captor.getValue().sourceService()).isEqualTo("catalog-service");
        assertThat(captor.getValue().action()).isEqualTo("UPDATE");
    }

    @Test
    void shouldUseDetailPermissionFlagForDetailEndpoint() throws Exception {
        doNothing().when(auditAuthorizer).requireRead(principal);
        when(auditAuthorizer.canReadDetails(principal)).thenReturn(false);
        when(auditQueryService.getAuditEvent(principal, 1L, false)).thenReturn(new AuditEventDetailResponse(
                1L,
                "evt-1",
                "iam-service",
                "iam",
                "iam.user.created",
                Instant.parse("2026-03-27T10:00:00Z"),
                Instant.parse("2026-03-27T10:00:01Z"),
                "idem-1",
                "corr-1",
                1L,
                2L,
                3L,
                "CREATE",
                "USER",
                "3",
                "SUCCESS",
                null,
                null,
                null,
                true,
                "CREATE | USER | 3 | SUCCESS"
        ));

        authenticate(principal);
        mockMvc.perform(get("/audit/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.detailMasked").value(true));

        verify(auditQueryService).getAuditEvent(principal, 1L, false);
    }

    private void authenticate(FernPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
    }
}
