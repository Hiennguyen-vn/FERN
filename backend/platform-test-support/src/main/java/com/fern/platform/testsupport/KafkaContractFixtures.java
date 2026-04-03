package com.fern.platform.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class KafkaContractFixtures {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String ROOT = "/kafka-contract-fixtures/";

    private KafkaContractFixtures() {
    }

    public static String load(String fixtureName) {
        try (InputStream inputStream = KafkaContractFixtures.class.getResourceAsStream(ROOT + fixtureName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Kafka contract fixture not found: " + fixtureName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load kafka contract fixture " + fixtureName, exception);
        }
    }

    public static <T> T read(String fixtureName, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(load(fixtureName), type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to deserialize kafka contract fixture " + fixtureName, exception);
        }
    }
}
