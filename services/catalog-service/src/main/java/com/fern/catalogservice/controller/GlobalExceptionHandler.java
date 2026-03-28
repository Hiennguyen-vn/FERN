package com.fern.catalogservice.controller;

import com.fern.platform.common.ApiErrorResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.observability.CorrelationId;
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
        log.error(
                "catalog_unhandled_exception correlationId={} path={} exceptionType={}",
                request.getHeader(CorrelationId.HEADER),
                request.getRequestURI(),
                exception.getClass().getName(),
                exception
        );
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
