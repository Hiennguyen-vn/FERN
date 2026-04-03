package com.fern.financeservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.web.FernGlobalExceptionHandler;
import com.fern.financeservice.observability.FinanceUnhandledExceptionListener;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    private RecordingAlertPublisher operationalAlertPublisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        operationalAlertPublisher = new RecordingAlertPublisher();
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new FernGlobalExceptionHandler(
                        "finance-service",
                        List.of(new FinanceUnhandledExceptionListener(new SimpleMeterRegistry(), operationalAlertPublisher))))
                .build();
    }

    @Test
    void shouldReturnGenericInternalErrorMessage() throws Exception {
        mockMvc.perform(get("/boom").header(CorrelationId.HEADER, "corr-finance"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value(ExceptionSummaries.unexpectedErrorMessage()))
                .andExpect(jsonPath("$.correlationId").value("corr-finance"))
                .andExpect(content().string(not(containsString("secret payroll detail"))));
    }

    @Test
    void shouldSanitizePayrollRunAlertDetails() throws Exception {
        mockMvc.perform(get("/payroll-runs/1").header(CorrelationId.HEADER, "corr-payroll"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(ExceptionSummaries.unexpectedErrorMessage()));

        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.alertType).isEqualTo("PAYROLL_RUN_FAILED");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.severity).isEqualTo("HIGH");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.summary).isEqualTo("Payroll run operation failed");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.correlationId).isEqualTo("corr-payroll");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.regionId).isNull();
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.outletId).isNull();
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.entityType).isEqualTo("HTTP_REQUEST");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.entityId).isEqualTo("/payroll-runs/1");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.details)
                .containsEntry("errorMessage", "RuntimeException")
                .containsEntry("path", "/payroll-runs/1");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.details.toString())
                .doesNotContain("secret payroll detail");
    }

    @Test
    void shouldReturnValidationErrorForInvalidRequest() throws Exception {
        mockMvc.perform(post("/validate")
                        .header(CorrelationId.HEADER, "corr-finance-valid")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.correlationId").value("corr-finance-valid"))
                .andExpect(jsonPath("$.details.name").value("must not be blank"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("secret payroll detail");
        }

        @GetMapping("/payroll-runs/1")
        String payrollRun() {
            throw new RuntimeException("secret payroll detail");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody ValidationRequest request) {
            return request.name();
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }

    static final class RecordingAlertPublisher implements OperationalAlertPublisher {
        private String alertType;
        private String severity;
        private String summary;
        private String correlationId;
        private Long regionId;
        private Long outletId;
        private String entityType;
        private String entityId;
        private Map<String, Object> details;

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
            this.alertType = alertType;
            this.severity = severity;
            this.summary = summary;
            this.correlationId = correlationId;
            this.regionId = regionId;
            this.outletId = outletId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.details = details;
        }
    }
}
