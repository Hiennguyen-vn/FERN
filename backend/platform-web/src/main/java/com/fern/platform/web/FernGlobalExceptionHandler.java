package com.fern.platform.web;

import com.fern.platform.common.ApiErrorResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.UnauthorizedException;
import com.fern.platform.observability.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FernGlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(FernGlobalExceptionHandler.class);
    private final String applicationName;
    private final List<UnhandledExceptionListener> unhandledListeners;

    public FernGlobalExceptionHandler(String applicationName, List<UnhandledExceptionListener> unhandledListeners) {
        this.applicationName = applicationName;
        this.unhandledListeners = List.copyOf(unhandledListeners);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "unauthorized", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "resource_not_found", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "conflict", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiErrorResponse> handleJwt(JwtException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, Object> details = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage(),
                        (left, right) -> left
                ));
        return build(HttpStatus.BAD_REQUEST, "validation_error", "Request validation failed", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, Object> details = exception.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (left, right) -> left
                ));
        return build(HttpStatus.BAD_REQUEST, "validation_error", "Request validation failed", request, details);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestBinding(ServletRequestBindingException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DownstreamUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleDownstreamUnavailable(DownstreamUnavailableException exception, HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "downstream_unavailable", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", "Request violates data constraints", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleOther(Exception exception, HttpServletRequest request) {
        log.error(
                "{}_unhandled_exception correlationId={} path={} exceptionType={}",
                serviceLogLabel(),
                request.getHeader(CorrelationId.HEADER),
                request.getRequestURI(),
                exception.getClass().getName(),
                exception
        );
        for (UnhandledExceptionListener listener : unhandledListeners) {
            try {
                listener.onUnhandled(request, exception);
            } catch (RuntimeException suppressed) {
                log.warn("unhandled_exception_listener_failed", suppressed);
            }
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", ExceptionSummaries.unexpectedErrorMessage(), request, Map.of());
    }

    private String serviceLogLabel() {
        String name = applicationName;
        if (name.endsWith("-service")) {
            name = name.substring(0, name.length() - "-service".length());
        }
        return name.replace('-', '_');
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
