package com.fern.platform.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.UnauthorizedException;
import java.io.IOException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class FernDownstreamErrorMapper {
    private final ObjectMapper objectMapper;

    public FernDownstreamErrorMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuntimeException translateResponse(FernDownstreamClientSpec spec, RestClientResponseException exception) {
        String message = extractMessage(exception);
        return switch (exception.getStatusCode().value()) {
            case 400, 422 -> new BadRequestException(message);
            case 401 -> new UnauthorizedException(message);
            case 403 -> new ForbiddenException(message);
            case 404 -> new ResourceNotFoundException(message);
            case 409 -> new ConflictException(message);
            case 429 -> new DownstreamUnavailableException(spec.targetService() + " is unavailable", exception);
            default -> exception.getStatusCode().is5xxServerError()
                    ? new DownstreamUnavailableException(spec.targetService() + " is unavailable", exception)
                    : new DownstreamUnavailableException(spec.targetService() + " is unavailable", exception);
        };
    }

    public DownstreamUnavailableException translateUnavailable(FernDownstreamClientSpec spec, Exception exception) {
        return new DownstreamUnavailableException(spec.targetService() + " is unavailable", exception);
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
            // Fall back to the raw response body if it is not JSON.
        }
        return body;
    }
}
