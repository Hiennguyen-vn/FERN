package com.fern.inventoryservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.observability.CorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(new SimpleMeterRegistry()))
                .build();
    }

    @Test
    void shouldReturnGenericInternalErrorMessage() throws Exception {
        mockMvc.perform(get("/boom").header(CorrelationId.HEADER, "corr-inventory"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value(ExceptionSummaries.unexpectedErrorMessage()))
                .andExpect(jsonPath("$.correlationId").value("corr-inventory"))
                .andExpect(content().string(not(containsString("secret production detail"))));
    }

    @Test
    void shouldReturnValidationErrorForInvalidRequest() throws Exception {
        mockMvc.perform(post("/validate")
                        .header(CorrelationId.HEADER, "corr-inventory-valid")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.correlationId").value("corr-inventory-valid"))
                .andExpect(jsonPath("$.details.name").value("must not be blank"));
    }

    @Test
    void shouldReturnServiceUnavailableForDownstreamFailure() throws Exception {
        mockMvc.perform(get("/downstream").header(CorrelationId.HEADER, "corr-inventory-downstream"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("downstream_unavailable"))
                .andExpect(jsonPath("$.message").value("org-service is unavailable while resolving outlet 201"))
                .andExpect(jsonPath("$.correlationId").value("corr-inventory-downstream"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("secret production detail");
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
}
