package com.fern.posservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.DownstreamUnavailableException;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PosDownstreamErrorHandler {
    private final ObjectMapper objectMapper;

    public PosDownstreamErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuntimeException translateResponse(String serviceName, RestClientResponseException exception) {
        String message = extractMessage(exception);
        if (exception.getStatusCode().is5xxServerError()) {
            return new DownstreamUnavailableException(serviceName + " is unavailable", exception);
        }
        return switch (exception.getStatusCode().value()) {
            case 400 -> new BadRequestException(message);
            case 404, 409 -> new ConflictException(message);
            default -> new DownstreamUnavailableException(serviceName + " is unavailable", exception);
        };
    }

    public DownstreamUnavailableException serviceUnavailable(String serviceName, Exception exception) {
        return new DownstreamUnavailableException(serviceName + " is unavailable", exception);
    }

    private String extractMessage(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return exception.getStatusText();
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode message = json.path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (IOException ignored) {
            // Fall back to the raw body when the response is not JSON.
        }
        return body;
    }
}
