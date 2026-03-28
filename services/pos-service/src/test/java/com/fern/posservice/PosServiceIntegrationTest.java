package com.fern.posservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PosServiceIntegrationTest {
    private static HttpServer catalogServer;
    private static HttpServer inventoryServer;
    private static volatile String lastCatalogAuthorization;
    private static volatile String lastCatalogActorUserId;
    private static volatile String lastInventoryAuthorization;
    private static volatile String lastInventoryActorUserId;
    private static volatile boolean delayCatalogMenuResponse;
    private static volatile int recipeBatchRequestCount;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensureServersStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("pos"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.clients.catalog.base-url", () -> "http://localhost:" + catalogServer.getAddress().getPort());
        registry.add("fern.clients.catalog.connect-timeout", () -> "500ms");
        registry.add("fern.clients.catalog.read-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost:" + inventoryServer.getAddress().getPort());
        registry.add("fern.outbox.max-attempts", () -> "3");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("catalogCircuitBreaker")
    private CircuitBreaker catalogCircuitBreaker;

    @Autowired
    @Qualifier("inventoryCircuitBreaker")
    private CircuitBreaker inventoryCircuitBreaker;

    private String token;

    @BeforeAll
    static void startServers() throws IOException {
        catalogServer = HttpServer.create(new InetSocketAddress(0), 0);
        catalogServer.createContext("/internal/catalog/menu", exchange -> {
            lastCatalogAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastCatalogActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            if (delayCatalogMenuResponse) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = """
                    {
                      "items": [
                        {
                          "productId": 10,
                          "productCode": "LATTE",
                          "productName": "Latte",
                          "categoryCode": "BEV",
                          "currencyCode": "VND",
                          "priceValue": 50.00,
                          "taxPercent": 10.00
                        },
                        {
                          "productId": 11,
                          "productCode": "ESP",
                          "productName": "Espresso",
                          "categoryCode": "BEV",
                          "currencyCode": "VND",
                          "priceValue": 30.00,
                          "taxPercent": 5.00
                        }
                      ]
                    }
                    """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        catalogServer.createContext("/internal/catalog/recipe-resolutions", exchange -> {
            lastCatalogAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastCatalogActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            recipeBatchRequestCount++;
            String query = exchange.getRequestURI().getQuery();
            boolean includesEspresso = query != null && query.contains("11");
            byte[] body = (includesEspresso
                    ? """
                    [
                      {
                        "productId": 10,
                        "recipeId": 20,
                        "recipeVersionId": 30,
                        "recipeCode": "RCP-LATTE",
                        "versionNo": "v1",
                        "effectiveFrom": "2026-03-01",
                        "ingredients": [
                          {
                            "ingredientId": 200,
                            "ingredientCode": "MILK",
                            "ingredientName": "Milk",
                            "uomCode": "L",
                            "qty": 1.5000,
                            "sortOrder": 1
                          }
                        ]
                      },
                      {
                        "productId": 11,
                        "recipeId": 21,
                        "recipeVersionId": 31,
                        "recipeCode": "RCP-ESP",
                        "versionNo": "v1",
                        "effectiveFrom": "2026-03-01",
                        "ingredients": [
                          {
                            "ingredientId": 201,
                            "ingredientCode": "BEAN",
                            "ingredientName": "Coffee Bean",
                            "uomCode": "GRAM",
                            "qty": 8.0000,
                            "sortOrder": 1
                          }
                        ]
                      }
                    ]
                    """
                    : """
                    [
                      {
                        "productId": 10,
                        "recipeId": 20,
                        "recipeVersionId": 30,
                        "recipeCode": "RCP-LATTE",
                        "versionNo": "v1",
                        "effectiveFrom": "2026-03-01",
                        "ingredients": [
                          {
                            "ingredientId": 200,
                            "ingredientCode": "MILK",
                            "ingredientName": "Milk",
                            "uomCode": "L",
                            "qty": 1.5000,
                            "sortOrder": 1
                          }
                        ]
                      }
                    ]
                    """).getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        catalogServer.start();

        inventoryServer = HttpServer.create(new InetSocketAddress(0), 0);
        inventoryServer.createContext("/internal/inventory/sale-reservations", exchange -> {
            lastInventoryAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastInventoryActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            byte[] body = """
                    {
                      "reservationId": 999,
                      "expiresAt": "2026-03-27T12:05:00Z"
                    }
                    """.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        inventoryServer.start();
    }

    @AfterAll
    static void stopServers() {
        if (catalogServer != null) {
            catalogServer.stop(0);
        }
        if (inventoryServer != null) {
            inventoryServer.stop(0);
        }
    }

    private static void ensureServersStarted() {
        if (catalogServer != null && inventoryServer != null) {
            return;
        }
        try {
            startServers();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start POS integration stubs", exception);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    pos.sale_snapshot,
                    pos.sale_payment,
                    pos.sale_order_line,
                    pos.sale_order,
                    pos.pos_session,
                    pos.outbox_event
                RESTART IDENTITY CASCADE
                """);
        lastCatalogAuthorization = null;
        lastCatalogActorUserId = null;
        lastInventoryAuthorization = null;
        lastInventoryActorUserId = null;
        delayCatalogMenuResponse = false;
        recipeBatchRequestCount = 0;
        catalogCircuitBreaker.reset();
        inventoryCircuitBreaker.reset();
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        token = issueToken(
                Set.of(
                        "pos.session.read",
                        "pos.session.open",
                        "pos.session.close",
                        "pos.session.reconcile",
                        "pos.order.read",
                        "pos.order.create",
                        "pos.order.update",
                        "pos.order.cancel",
                        "pos.order.complete",
                        "catalog.internal.resolve"
                ),
                List.of(1L),
                List.of(101L)
        );
    }

    @Test
    void shouldOpenSessionCreateOrderTakeSplitPaymentsAndCompleteSale() throws Exception {
        String sessionJson = mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.sessionCode").value(org.hamcrest.Matchers.startsWith("POSS-")))
                .andReturn().getResponse().getContentAsString();
        Long sessionId = readId(sessionJson);

        String orderJson = mockMvc.perform(post("/sale-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "DINE_IN",
                                  "lines": [
                                    {"productId": 10, "qty": 2.0000},
                                    {"productId": 11, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.startsWith("SO-")))
                .andExpect(jsonPath("$.totalAmount").value(141.50))
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 60.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-2")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 81.50,
                                  "transactionRef": "txn-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        String snapshot = jdbcTemplate.queryForObject("""
                SELECT order_snapshot::text
                FROM pos.sale_snapshot
                WHERE sale_order_id = ?
                """, String.class, orderId);
        assertThat(objectMapper.readTree(snapshot).path("reservationId").asLong()).isEqualTo(999L);

        String eventType = jdbcTemplate.queryForObject("""
                SELECT event_type
                FROM pos.outbox_event
                WHERE aggregate_id = ?
                """, String.class, orderId.toString());
        assertThat(eventType).isEqualTo("pos.sale.completed");
        assertThat(recipeBatchRequestCount).isEqualTo(1);
        assertThat(lastCatalogActorUserId).isEqualTo("1");
        assertThat(lastInventoryActorUserId).isEqualTo("1");
        assertThat(lastCatalogAuthorization).isNotBlank().isNotEqualTo(bearer());
        assertThat(lastInventoryAuthorization).isNotBlank().isNotEqualTo(bearer());

        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        assertThat(jwtService.decode(lastCatalogAuthorization.substring("Bearer ".length())).principalType())
                .isEqualTo(FernPrincipalType.SERVICE);
        assertThat(jwtService.decode(lastInventoryAuthorization.substring("Bearer ".length())).principalType())
                .isEqualTo(FernPrincipalType.SERVICE);
    }

    @Test
    void shouldRejectSecondOpenSessionForSameOutlet() throws Exception {
        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRequireOrderUpdatePermissionToAddPayment() throws Exception {
        String sessionJson = mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = readId(sessionJson);

        String orderJson = mockMvc.perform(post("/sale-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "DINE_IN",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        String createOnlyToken = issueToken(
                Set.of(
                        "pos.session.read",
                        "pos.session.open",
                        "pos.order.read",
                        "pos.order.create"
                ),
                List.of(1L),
                List.of(101L)
        );

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", "Bearer " + createOnlyToken)
                        .header("Idempotency-Key", "pay-no-update")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 20.00
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectMismatchedRegionScopeEvenWhenOutletMatches() throws Exception {
        String wrongRegionToken = issueToken(
                Set.of("pos.session.open"),
                List.of(999L),
                List.of(101L)
        );

        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", "Bearer " + wrongRegionToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnServiceUnavailableAndAvoidWritesWhenCatalogTimesOut() throws Exception {
        String sessionJson = mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = readId(sessionJson);

        delayCatalogMenuResponse = true;
        try {
            mockMvc.perform(post("/sale-orders")
                            .header("Authorization", bearer())
                            .contentType("application/json")
                            .content("""
                                    {
                                      "posSessionId": %d,
                                      "orderType": "DINE_IN",
                                      "lines": [
                                        {"productId": 10, "qty": 1.0000}
                                      ]
                                    }
                                    """.formatted(sessionId)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("downstream_unavailable"));
        } finally {
            delayCatalogMenuResponse = false;
        }

        Long orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.sale_order", Long.class);
        assertThat(orderCount).isEqualTo(0L);
    }

    private Long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String issueToken(Set<String> permissions, List<Long> regions, List<Long> outlets) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        return jwtService.encode(new FernJwtClaims(
                1L,
                "pos-tester",
                Set.of("outlet_manager"),
                permissions,
                new ScopeRoots(regions, outlets),
                1L,
                1L,
                "pos-test-jti-" + permissions.hashCode() + "-" + regions.hashCode() + "-" + outlets.hashCode(),
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
    }
}
