package com.fern.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReportDeserializationFailureRecorder {
    private static final Instant DESERIALIZATION_OCCURRED_AT = Instant.EPOCH;
    private static final String SOURCE_SERVICE = "report-service";

    private final ReportIngestionSupport support;

    public ReportDeserializationFailureRecorder(ReportIngestionSupport support) {
        this.support = support;
    }

    public void record(String topic, String payload, Class<?> expectedType, JsonProcessingException cause) {
        String failureMessage = "Unable to deserialize " + expectedType.getSimpleName();
        String syntheticId = "deser:" + topic + ":" + stableHash(topic, expectedType.getName(), payload);
        IllegalArgumentException failure = new IllegalArgumentException(failureMessage, cause);
        try {
            support.ingestWithLanding(
                    List.of(),
                    syntheticId,
                    SOURCE_SERVICE,
                    expectedType.getSimpleName() + ".deserialization_failed",
                    DESERIALIZATION_OCCURRED_AT,
                    syntheticId,
                    topic,
                    support.toJson(Map.of(
                            "topic", topic,
                            "expectedType", expectedType.getName(),
                            "rawPayload", payload,
                            "failureType", cause.getClass().getSimpleName(),
                            "failureMessage", failureMessage
                    )),
                    () -> {
                        throw failure;
                    }
            );
        } catch (RuntimeException recorded) {
            if (recorded != failure) {
                throw recorded;
            }
        }
    }

    private String stableHash(String topic, String expectedType, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(topic.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(expectedType.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
