package com.fern.financeservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.observability.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    private OperationalAlertPublisher operationalAlertPublisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        operationalAlertPublisher = mock(OperationalAlertPublisher.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(new SimpleMeterRegistry(), operationalAlertPublisher))
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

        verify(operationalAlertPublisher).publish(
                eq("PAYROLL_RUN_FAILED"),
                eq("HIGH"),
                eq("Payroll run operation failed"),
                eq("corr-payroll"),
                isNull(),
                isNull(),
                eq("HTTP_REQUEST"),
                eq("/payroll-runs/1"),
                argThat(details -> details != null
                        && "RuntimeException".equals(details.get("errorMessage"))
                        && "/payroll-runs/1".equals(details.get("path"))
                        && !details.toString().contains("secret payroll detail"))
        );
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
    }
}
