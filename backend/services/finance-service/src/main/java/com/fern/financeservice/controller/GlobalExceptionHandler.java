package com.fern.financeservice.controller;

import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ApiErrorResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.observability.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Counter payrollRunFailureCounter;
    private final OperationalAlertPublisher operationalAlertPublisher;

    public GlobalExceptionHandler(MeterRegistry meterRegistry, OperationalAlertPublisher operationalAlertPublisher) {
        this.payrollRunFailureCounter = Counter.builder("fern_payroll_run_failures_total").register(meterRegistry);
        this.operationalAlertPublisher = operationalAlertPublisher;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "resource_not_found", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "conflict", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage(),
                        (left, right) -> left
                ));
        return build(HttpStatus.BAD_REQUEST, "validation_error", "Request validation failed", request, details);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleOther(Exception exception, HttpServletRequest request) {
        String sanitizedError = ExceptionSummaries.safeSummary(exception);
        log.error(
                "finance_unhandled_exception correlationId={} path={} exceptionType={}",
                request.getHeader(CorrelationId.HEADER),
                request.getRequestURI(),
                exception.getClass().getName(),
                exception
        );
        if (request.getRequestURI() != null && request.getRequestURI().contains("/payroll-runs/")) {
            payrollRunFailureCounter.increment();
            operationalAlertPublisher.publish(
                    "PAYROLL_RUN_FAILED",
                    "HIGH",
                    "Payroll run operation failed",
                    request.getHeader(CorrelationId.HEADER),
                    null,
                    null,
                    "HTTP_REQUEST",
                    request.getRequestURI(),
                    Map.of("errorMessage", sanitizedError, "path", request.getRequestURI())
            );
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", ExceptionSummaries.unexpectedErrorMessage(), request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                Instant.now(),
                request.getHeader(CorrelationId.HEADER),
                details
        ));
    }
}
