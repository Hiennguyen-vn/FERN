package com.fern.platform.testsupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class JsonTestSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonTestSupport() {
    }

    public static boolean jsonEquals(String expectedJson, String actualJson) {
        try {
            JsonNode expected = OBJECT_MAPPER.readTree(expectedJson);
            JsonNode actual = OBJECT_MAPPER.readTree(actualJson);
            return expected.equals(actual);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to compare JSON payloads", exception);
        }
    }

    public static boolean matchesProducerRecord(
            ProducerRecord<String, String> record,
            String expectedTopic,
            String expectedKey,
            String expectedJson
    ) {
        return expectedTopic.equals(record.topic())
                && expectedKey.equals(record.key())
                && jsonEquals(expectedJson, record.value());
    }
}
