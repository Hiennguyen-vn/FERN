package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.platform.testsupport.JsonTestSupport;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
class PosSaleProducerIntegrationContractTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("pos"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "pos-producer-contract-secret-012345678901234567890123456");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.clients.catalog.base-url", () -> "http://localhost");
        registry.add("fern.clients.catalog.connect-timeout", () -> "500ms");
        registry.add("fern.clients.catalog.read-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost");
        registry.add("fern.clients.inventory.connect-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.read-timeout", () -> "500ms");
        registry.add("fern.clients.org.base-url", () -> "http://localhost");
    }

    @Autowired
    private PosOrderService posOrderService;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE pos.outbox_event
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldPersistPosSaleCompletedPayloadToSharedFixtureContract() throws Exception {
        ReflectionTestUtils.invokeMethod(
                posOrderService,
                "enqueueSaleCompletedEvent",
                namedParameterJdbcTemplate,
                new OrderRecord(
                        100L,
                        "SO-000100",
                        1L,
                        101L,
                        200L,
                        "VND",
                        "DINE_IN",
                        "OPEN",
                        "PAID",
                        new BigDecimal("90.91"),
                        BigDecimal.ZERO,
                        new BigDecimal("9.09"),
                        new BigDecimal("100.00"),
                        "fixture order",
                        Instant.parse("2026-03-29T10:00:00Z"),
                        Instant.parse("2026-03-29T10:05:00Z"),
                        999L
                ),
                new SessionRecord(
                        200L,
                        "SES-000200",
                        1L,
                        101L,
                        "T-01",
                        "VND",
                        5L,
                        null,
                        LocalDate.parse("2026-03-29"),
                        "OPEN",
                        null,
                        Instant.parse("2026-03-29T09:00:00Z"),
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new PricingSnapshot(List.of(), new BigDecimal("90.91"), new BigDecimal("9.09"), new BigDecimal("100.00")),
                List.of(
                        new com.fern.posservice.dto.PosResponses.SalePaymentResponse(
                                50L,
                                "CASH",
                                new BigDecimal("60.00"),
                                "CAPTURED",
                                Instant.parse("2026-03-29T10:04:00Z"),
                                null
                        ),
                        new com.fern.posservice.dto.PosResponses.SalePaymentResponse(
                                51L,
                                "CARD",
                                new BigDecimal("40.00"),
                                "CAPTURED",
                                Instant.parse("2026-03-29T10:04:30Z"),
                                "txn-card-1"
                        )
                ),
                saleSnapshot(),
                List.of(new RecipeUsageItem(200L, "MILK", "Fresh Milk", "ML", new BigDecimal("180.00"))),
                new SaleReservationResponse(999L, Instant.parse("2026-03-29T10:20:00Z")),
                principal(),
                Instant.parse("2026-03-29T10:05:00Z"),
                "corr-sale-fixture"
        );

        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM pos.outbox_event
                WHERE aggregate_type = 'SALE_ORDER'
                  AND aggregate_id = '100'
                  AND event_type = 'pos.sale.completed'
                """, String.class);

        assertThat(JsonTestSupport.jsonEquals(expectedFixtureJson(payload), payload)).isTrue();
    }

    private FernPrincipal principal() {
        return new FernPrincipal(
                5L,
                "pos-producer-contract",
                Set.of("pos"),
                Set.of(),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "pos-producer-contract-jti"
        );
    }

    private Map<String, Object> saleSnapshot() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", 10);
        line.put("qty", 2);
        line.put("lineTotal", new BigDecimal("100.00"));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderId", 100L);
        snapshot.put("lines", List.of(line));
        return snapshot;
    }

    private String expectedFixtureJson(String actualJson) throws Exception {
        ObjectNode expected = (ObjectNode) objectMapper.readTree(loadFixture("pos.sale.completed.json"));
        JsonNode actual = objectMapper.readTree(actualJson);
        expected.put("eventId", actual.path("eventId").asText());
        return objectMapper.writeValueAsString(expected);
    }

    private String loadFixture(String fixtureName) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/kafka-contract-fixtures/" + fixtureName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
