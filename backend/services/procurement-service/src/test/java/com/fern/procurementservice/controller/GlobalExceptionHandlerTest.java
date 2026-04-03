package com.fern.procurementservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.web.FernGlobalExceptionHandler;
import com.fern.procurementservice.observability.ProcurementUnhandledExceptionListener;
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
                        "procurement-service",
                        List.of(new ProcurementUnhandledExceptionListener(new SimpleMeterRegistry(), operationalAlertPublisher))))
                .build();
    }

    @Test
    void shouldReturnGenericInternalErrorMessage() throws Exception {
        mockMvc.perform(get("/boom").header(CorrelationId.HEADER, "corr-procurement"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value(ExceptionSummaries.unexpectedErrorMessage()))
                .andExpect(jsonPath("$.correlationId").value("corr-procurement"))
                .andExpect(content().string(not(containsString("secret goods receipt detail"))));
    }

    @Test
    void shouldSanitizeGoodsReceiptPostAlertDetails() throws Exception {
        mockMvc.perform(get("/goods-receipts/1/post").header(CorrelationId.HEADER, "corr-gr"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(ExceptionSummaries.unexpectedErrorMessage()));

        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.alertType).isEqualTo("GOODS_RECEIPT_POST_FAILED");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.severity).isEqualTo("HIGH");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.summary).isEqualTo("Goods receipt posting failed");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.correlationId).isEqualTo("corr-gr");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.regionId).isNull();
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.outletId).isNull();
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.entityType).isEqualTo("HTTP_REQUEST");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.entityId).isEqualTo("/goods-receipts/1/post");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.details)
                .containsEntry("errorMessage", "RuntimeException")
                .containsEntry("path", "/goods-receipts/1/post");
        org.assertj.core.api.Assertions.assertThat(operationalAlertPublisher.details.toString())
                .doesNotContain("secret goods receipt detail");
    }

    @Test
    void shouldReturnValidationErrorForInvalidRequest() throws Exception {
        mockMvc.perform(post("/validate")
                        .header(CorrelationId.HEADER, "corr-procurement-valid")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.correlationId").value("corr-procurement-valid"))
                .andExpect(jsonPath("$.details.name").value("must not be blank"));
    }

    @Test
    void shouldReturnServiceUnavailableForDownstreamFailure() throws Exception {
        mockMvc.perform(get("/downstream").header(CorrelationId.HEADER, "corr-procurement-downstream"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("downstream_unavailable"))
                .andExpect(jsonPath("$.message").value("org-service is unavailable while resolving outlet 201"))
                .andExpect(jsonPath("$.correlationId").value("corr-procurement-downstream"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("secret goods receipt detail");
        }

        @GetMapping("/goods-receipts/1/post")
        String postGoodsReceipt() {
            throw new RuntimeException("secret goods receipt detail");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody ValidationRequest request) {
            return request.name();
        }

        @GetMapping("/downstream")
        String downstream() {
            throw new DownstreamUnavailableException("org-service is unavailable while resolving outlet 201");
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
