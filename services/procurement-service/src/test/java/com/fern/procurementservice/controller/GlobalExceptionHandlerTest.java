package com.fern.procurementservice.controller;

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

        verify(operationalAlertPublisher).publish(
                eq("GOODS_RECEIPT_POST_FAILED"),
                eq("HIGH"),
                eq("Goods receipt posting failed"),
                eq("corr-gr"),
                isNull(),
                isNull(),
                eq("HTTP_REQUEST"),
                eq("/goods-receipts/1/post"),
                argThat(details -> details != null
                        && "RuntimeException".equals(details.get("errorMessage"))
                        && "/goods-receipts/1/post".equals(details.get("path"))
                        && !details.toString().contains("secret goods receipt detail"))
        );
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
    }
}
