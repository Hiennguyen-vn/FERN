package com.fern.reportservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.ConflictException;
import com.fern.platform.observability.CorrelationId;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnGenericInternalErrorMessage() throws Exception {
        mockMvc.perform(get("/boom").header(CorrelationId.HEADER, "corr-report"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value(ExceptionSummaries.unexpectedErrorMessage()))
                .andExpect(jsonPath("$.correlationId").value("corr-report"))
                .andExpect(content().string(not(containsString("secret production detail"))));
    }

    @Test
    void shouldReturnValidationErrorForInvalidRequest() throws Exception {
        mockMvc.perform(post("/validate")
                        .header(CorrelationId.HEADER, "corr-report-valid")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.correlationId").value("corr-report-valid"))
                .andExpect(jsonPath("$.details.name").value("must not be blank"));
    }

    @Test
    void shouldReturnConflictError() throws Exception {
        mockMvc.perform(get("/conflict").header(CorrelationId.HEADER, "corr-report-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"))
                .andExpect(jsonPath("$.message").value("conflicting export request"))
                .andExpect(jsonPath("$.correlationId").value("corr-report-conflict"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("secret production detail");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ConflictException("conflicting export request");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody ValidationRequest request) {
            return request.name();
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }
}
