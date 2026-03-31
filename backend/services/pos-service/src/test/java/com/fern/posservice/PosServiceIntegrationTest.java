package com.fern.posservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.posservice.config.PosOutboxProperties;
import com.fern.posservice.service.PosOrgClient;
import com.fern.posservice.service.PosOutboxPublisher;
import com.fern.posservice.service.PosStore;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PosServiceIntegrationTest {
    private static final Set<String> POS_FULL_ACCESS_PERMISSIONS = Set.of(
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
    );
    private static HttpServer catalogServer;
    private static HttpServer inventoryServer;
    private static HttpServer orgServer;
    private static volatile String lastCatalogAuthorization;
    private static volatile String lastCatalogActorUserId;
    private static volatile String lastInventoryAuthorization;
    private static volatile String lastInventoryActorUserId;
    private static volatile int inventoryReleaseRequestCount;
    private static volatile boolean delayInventoryReservationResponse;
    private static volatile long inventoryReservationDelayMillis;
    private static volatile Long lastReleasedReservationId;
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
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.clients.catalog.base-url", () -> "http://localhost:" + catalogServer.getAddress().getPort());
        registry.add("fern.clients.catalog.connect-timeout", () -> "500ms");
        registry.add("fern.clients.catalog.read-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost:" + inventoryServer.getAddress().getPort());
        registry.add("fern.clients.inventory.connect-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.read-timeout", () -> "500ms");
        registry.add("fern.clients.org.base-url", () -> "http://localhost:" + orgServer.getAddress().getPort());
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

    @SpyBean
    private PosStore posStore;

    @SpyBean
    private PosOrgClient posOrgClient;

    @MockBean
    private OperationalAlertPublisher operationalAlertPublisher;

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
                        },
                        {
                          "productId": 12,
                          "productCode": "ROUND",
                          "productName": "Rounded Drink",
                          "categoryCode": "BEV",
                          "currencyCode": "VND",
                          "priceValue": 10.005,
                          "taxPercent": 7.75
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
            String path = exchange.getRequestURI().getPath();
            if ("/internal/inventory/sale-reservations".equals(path)) {
                if (delayInventoryReservationResponse) {
                    try {
                        Thread.sleep(inventoryReservationDelayMillis);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
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
                return;
            }
            if (path.matches("/internal/inventory/sale-reservations/\\d+/cancel")) {
                inventoryReleaseRequestCount++;
                String[] segments = path.split("/");
                lastReleasedReservationId = Long.parseLong(segments[segments.length - 2]);
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            byte[] body = "{}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        inventoryServer.start();

        orgServer = HttpServer.create(new InetSocketAddress(0), 0);
        orgServer.createContext("/outlets", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = 404;
            byte[] body = "{}".getBytes();
            if ("/outlets/101".equals(path)) {
                status = 200;
                body = """
                        {
                          "id": 101,
                          "regionId": 1
                        }
                        """.getBytes();
            } else if ("/outlets/102".equals(path)) {
                status = 200;
                body = """
                        {
                          "id": 102,
                          "regionId": 11
                        }
                        """.getBytes();
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        orgServer.start();
    }

    @AfterAll
    static void stopServers() {
        if (catalogServer != null) {
            catalogServer.stop(0);
        }
        if (inventoryServer != null) {
            inventoryServer.stop(0);
        }
        if (orgServer != null) {
            orgServer.stop(0);
        }
    }

    private static void ensureServersStarted() {
        if (catalogServer != null && inventoryServer != null && orgServer != null) {
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
        inventoryReleaseRequestCount = 0;
        delayInventoryReservationResponse = false;
        inventoryReservationDelayMillis = 1000L;
        lastReleasedReservationId = null;
        delayCatalogMenuResponse = false;
        recipeBatchRequestCount = 0;
        catalogCircuitBreaker.reset();
        inventoryCircuitBreaker.reset();
        reset(posStore, posOrgClient, operationalAlertPublisher);
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        token = issueToken(POS_FULL_ACCESS_PERMISSIONS, List.of(1L), List.of(101L));
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
                        .header("Authorization", bearer())
                        .header(CorrelationId.HEADER, "corr-complete-1"))
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
        String correlationId = jdbcTemplate.queryForObject("""
                SELECT payload ->> 'correlationId'
                FROM pos.outbox_event
                WHERE aggregate_id = ?
                """, String.class, orderId.toString());
        assertThat(eventType).isEqualTo("pos.sale.completed");
        assertThat(correlationId).isEqualTo("corr-complete-1");
        assertThat(recipeBatchRequestCount).isEqualTo(1);
        assertThat(lastCatalogActorUserId).isEqualTo("1");
        assertThat(lastInventoryActorUserId).isEqualTo("1");
        assertThat(lastCatalogAuthorization).isNotBlank().isNotEqualTo(bearer());
        assertThat(lastInventoryAuthorization).isNotBlank().isNotEqualTo(bearer());

        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        assertThat(jwtService.decode(lastCatalogAuthorization.substring("Bearer ".length())).principalType())
                .isEqualTo(FernPrincipalType.SERVICE);
        assertThat(jwtService.decode(lastInventoryAuthorization.substring("Bearer ".length())).principalType())
                .isEqualTo(FernPrincipalType.SERVICE);
    }

    @Test
    void shouldPersistSaleCompletedOutboxPayloadReadyForReconciliation() throws Exception {
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
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000},
                                    {"productId": 11, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-reconciliation-cash")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 60.00
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-reconciliation-card")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 26.50,
                                  "transactionRef": "txn-reconciliation-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                        .header("Authorization", bearer())
                        .header(CorrelationId.HEADER, "corr-reconciliation-outbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM pos.outbox_event
                WHERE aggregate_id = ?
                  AND event_type = 'pos.sale.completed'
                """, String.class, orderId.toString());
        JsonNode event = objectMapper.readTree(payload);

        assertThat(event.path("saleOrderId").asLong()).isEqualTo(orderId);
        assertThat(event.path("regionId").asLong()).isEqualTo(1L);
        assertThat(event.path("outletId").asLong()).isEqualTo(101L);
        assertThat(event.at("/businessDate/0").asInt()).isEqualTo(2026);
        assertThat(event.at("/businessDate/1").asInt()).isEqualTo(3);
        assertThat(event.at("/businessDate/2").asInt()).isEqualTo(27);
        assertThat(event.path("correlationId").asText()).isEqualTo("corr-reconciliation-outbox");
        assertThat(event.path("payments")).hasSize(2);
        assertThat(event.at("/saleSnapshot/orderId").asLong()).isEqualTo(orderId);
        assertThat(event.at("/saleSnapshot/outletId").asLong()).isEqualTo(101L);
        assertThat(event.at("/saleSnapshot/payments")).hasSize(2);
        assertThat(event.at("/saleSnapshot/totalAmount").decimalValue()).isEqualByComparingTo("86.50");
        assertThat(event.at("/saleSnapshot/reservationId").asLong()).isEqualTo(999L);
    }

    @Test
    void shouldRejectUnsupportedPaymentStatus() throws Exception {
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
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-invalid-status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 25.00,
                                  "status": "SUCCESSFUL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.status").value("Status must be SUCCESS, FAILED, or CANCELLED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos.sale_payment WHERE sale_order_id = ?",
                Integer.class,
                orderId
        )).isZero();
    }

    @Test
    void shouldDefaultPaymentStatusToSuccessWhenStatusMissingOrBlank() throws Exception {
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

        String firstOrderJson = mockMvc.perform(post("/sale-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long firstOrderId = readId(firstOrderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", firstOrderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-status-missing")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 55.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        String secondOrderJson = mockMvc.perform(post("/sale-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long secondOrderId = readId(secondOrderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", secondOrderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-status-blank")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 55.00,
                                  "status": "   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        List<String> storedStatuses = jdbcTemplate.query("""
                SELECT status
                FROM pos.sale_payment
                WHERE sale_order_id IN (?, ?)
                ORDER BY sale_order_id
                """, (rs, rowNum) -> rs.getString("status"), firstOrderId, secondOrderId);
        assertThat(storedStatuses).containsExactly("SUCCESS", "SUCCESS");
    }

    @Test
    void shouldDefaultPaymentStatusToSuccessWhenStatusExplicitlyNull() throws Exception {
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
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-status-null")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 55.00,
                                  "status": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.sale_payment
                WHERE sale_order_id = ?
                """, String.class, orderId)).isEqualTo("SUCCESS");
    }

    @Test
    void shouldCompleteOrderUsingStoredPricingWhenCatalogMenuTimesOut() throws Exception {
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
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(55.00))
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-complete-stored-pricing")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 55.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        delayCatalogMenuResponse = true;
        try {
            mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                            .header("Authorization", bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalAmount").value(55.00));
        } finally {
            delayCatalogMenuResponse = false;
        }

        assertThat(recipeBatchRequestCount).isEqualTo(1);
    }

    @Test
    void shouldReturnExistingOpenSessionForSecondOpenRequest() throws Exception {
        String firstResponse = mockMvc.perform(post("/pos-sessions")
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
                .andExpect(header().string("X-Session-Existed", "false"))
                .andReturn().getResponse().getContentAsString();
        Long firstSessionId = readId(firstResponse);

        String secondResponse = mockMvc.perform(post("/pos-sessions")
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
                .andExpect(header().string("X-Session-Existed", "true"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(readId(secondResponse)).isEqualTo(firstSessionId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.pos_session", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldRejectBlankOrTooLongTerminalId() throws Exception {
        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "   ",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.terminalId")
                        .value("Terminal ID must be 1-64 chars of letters, numbers, underscores, or hyphens"));

        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "%s",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """.formatted("T".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.terminalId")
                        .value("Terminal ID must be 1-64 chars of letters, numbers, underscores, or hyphens"));
    }

    @Test
    void shouldAcceptTerminalIdWithUnderscoreAndDigits() throws Exception {
        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "TERM-A_01",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalId").value("TERM-A_01"));
    }

    @Test
    void shouldAllowNullTerminalIdAndPersistNoTerminal() throws Exception {
        String response = mockMvc.perform(post("/pos-sessions")
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

        Long sessionId = readId(response);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT terminal_id
                FROM pos.pos_session
                WHERE id = ?
                """, String.class, sessionId)).isNull();
    }

    @Test
    void shouldRejectOpenSessionWhenAnotherCashierAlreadyOwnsIt() throws Exception {
        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "TERM-A",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Session-Existed", "false"));

        String secondCashierToken = issueToken(
                2L,
                "pos-cashier-2",
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

        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", "Bearer " + secondCashierToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "TERM-A",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.pos_session", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldCreateDistinctOrdersWhenTwoTerminalSessionsSubmitOrdersConcurrently() throws Exception {
        String terminalOneSessionJson = mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "TERM-A",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long terminalOneSessionId = readId(terminalOneSessionJson);

        String terminalTwoSessionJson = mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "terminalId": "TERM-B",
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long terminalTwoSessionId = readId(terminalTwoSessionJson);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstOrderFuture = executor.submit(() -> concurrentOrderCreate(start, ready, terminalOneSessionId, "terminal-a-order"));
            Future<String> secondOrderFuture = executor.submit(() -> concurrentOrderCreate(start, ready, terminalTwoSessionId, "terminal-b-order"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            String firstOrderJson = firstOrderFuture.get(5, TimeUnit.SECONDS);
            String secondOrderJson = secondOrderFuture.get(5, TimeUnit.SECONDS);

            JsonNode firstOrder = objectMapper.readTree(firstOrderJson);
            JsonNode secondOrder = objectMapper.readTree(secondOrderJson);
            assertThat(firstOrder.get("id").asLong()).isNotEqualTo(secondOrder.get("id").asLong());
            assertThat(firstOrder.get("orderNumber").asText()).isNotEqualTo(secondOrder.get("orderNumber").asText());
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.sale_order", Integer.class)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
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
    void shouldSerializeConcurrentStaffPaymentsOnSameOrderWithoutDoubleCharging() throws Exception {
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

        String staffOneToken = issueToken(
                11L,
                "staff-one",
                Set.of("pos.order.read", "pos.order.update"),
                List.of(1L),
                List.of(101L)
        );
        String staffTwoToken = issueToken(
                12L,
                "staff-two",
                Set.of("pos.order.read", "pos.order.update"),
                List.of(1L),
                List.of(101L)
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstPayment = executor.submit(() -> concurrentPaymentStatus(start, ready, orderId, "pay-concurrent-1", staffOneToken, "txn-staff-1"));
            Future<Integer> secondPayment = executor.submit(() -> concurrentPaymentStatus(start, ready, orderId, "pay-concurrent-2", staffTwoToken, "txn-staff-2"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(firstPayment.get(5, TimeUnit.SECONDS), secondPayment.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        BigDecimal successfulPaymentTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM pos.sale_payment
                WHERE sale_order_id = ?
                  AND status = 'SUCCESS'
                """, BigDecimal.class, orderId);
        String paymentStatus = jdbcTemplate.queryForObject("""
                SELECT payment_status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, orderId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos.sale_payment WHERE sale_order_id = ?",
                Integer.class,
                orderId
        )).isEqualTo(1);
        assertThat(successfulPaymentTotal).isEqualByComparingTo("30.00");
        assertThat(paymentStatus).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void shouldReplaySamePaymentIdempotencyKeyWithoutDoubleCharging() throws Exception {
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

        String firstResponse = mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-replay-same-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 30.00,
                                  "transactionRef": "txn-replay-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andReturn().getResponse().getContentAsString();

        String replayResponse = mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-replay-same-key")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 30.00,
                                  "transactionRef": "txn-replay-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(firstResponse).path("id").asLong())
                .isEqualTo(objectMapper.readTree(replayResponse).path("id").asLong());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos.sale_payment WHERE sale_order_id = ?",
                Integer.class,
                orderId
        )).isEqualTo(1);
        BigDecimal successfulPaymentTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM pos.sale_payment
                WHERE sale_order_id = ?
                  AND status = 'SUCCESS'
                """, BigDecimal.class, orderId);
        assertThat(successfulPaymentTotal).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldRejectPaymentReplayWhenIdempotencyKeyTargetsDifferentOrder() throws Exception {
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

        String firstOrderJson = mockMvc.perform(post("/sale-orders")
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
        Long firstOrderId = readId(firstOrderJson);

        String secondOrderJson = mockMvc.perform(post("/sale-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 11, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long secondOrderId = readId(secondOrderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", firstOrderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-replay-cross-order")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 30.00,
                                  "transactionRef": "txn-replay-cross-order-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));

        mockMvc.perform(post("/sale-orders/{id}/payments", secondOrderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-replay-cross-order")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 31.50
                                }
                                """))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.sale_payment
                WHERE idempotency_key = 'pay-replay-cross-order'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT payment_status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, secondOrderId)).isEqualTo("UNPAID");
    }

    @Test
    void shouldReturnSameOrderStateForConcurrentPaymentsSharingIdempotencyKey() throws Exception {
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

        String staffOneToken = issueToken(
                11L,
                "staff-one",
                Set.of("pos.order.read", "pos.order.update"),
                List.of(1L),
                List.of(101L)
        );
        String staffTwoToken = issueToken(
                12L,
                "staff-two",
                Set.of("pos.order.read", "pos.order.update"),
                List.of(1L),
                List.of(101L)
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstPayment = executor.submit(() -> concurrentPaymentStatus(start, ready, orderId, "pay-shared-key", staffOneToken, "txn-shared-key"));
            Future<Integer> secondPayment = executor.submit(() -> concurrentPaymentStatus(start, ready, orderId, "pay-shared-key", staffTwoToken, "txn-shared-key"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(firstPayment.get(5, TimeUnit.SECONDS), secondPayment.get(5, TimeUnit.SECONDS)))
                    .containsExactly(200, 200);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos.sale_payment WHERE sale_order_id = ?",
                Integer.class,
                orderId
        )).isEqualTo(1);
        BigDecimal successfulPaymentTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM pos.sale_payment
                WHERE sale_order_id = ?
                  AND status = 'SUCCESS'
                """, BigDecimal.class, orderId);
        String paymentStatus = jdbcTemplate.queryForObject("""
                SELECT payment_status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, orderId);
        assertThat(successfulPaymentTotal).isEqualByComparingTo("30.00");
        assertThat(paymentStatus).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void shouldRejectClosingSessionWhilePaymentIsStillInFlightOnOpenOrder() throws Exception {
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

        CountDownLatch paymentPaused = new CountDownLatch(1);
        CountDownLatch allowPaymentToContinue = new CountDownLatch(1);
        AtomicBoolean intercepted = new AtomicBoolean(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object record = invocation.callRealMethod();
            if (Objects.equals(invocation.getArgument(0), orderId) && intercepted.compareAndSet(false, true)) {
                paymentPaused.countDown();
                assertThat(allowPaymentToContinue.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return record;
        }).when(posStore).requireOrderForUpdate(orderId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> paymentFuture = executor.submit(() -> mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                            .header("Authorization", bearer())
                            .header("Idempotency-Key", "pay-inflight-close")
                            .contentType("application/json")
                            .content("""
                                    {
                                      "paymentMethod": "CASH",
                                      "amount": 20.00
                                    }
                                    """))
                    .andReturn().getResponse().getStatus());

            assertThat(paymentPaused.await(5, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(post("/pos-sessions/{id}/close", sessionId)
                            .header("Authorization", bearer()))
                    .andExpect(status().isConflict());

            allowPaymentToContinue.countDown();
            assertThat(paymentFuture.get(5, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            allowPaymentToContinue.countDown();
            executor.shutdownNow();
        }

        String sessionStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.pos_session
                WHERE id = ?
                """, String.class, sessionId);
        String paymentStatus = jdbcTemplate.queryForObject("""
                SELECT payment_status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, orderId);
        assertThat(sessionStatus).isEqualTo("OPEN");
        assertThat(paymentStatus).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void shouldRejectConcurrentOrderUpdateAfterPaymentCommits() throws Exception {
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

        CountDownLatch paymentPaused = new CountDownLatch(1);
        CountDownLatch allowPaymentToContinue = new CountDownLatch(1);
        AtomicBoolean intercepted = new AtomicBoolean(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            Object record = invocation.callRealMethod();
            if (Objects.equals(invocation.getArgument(0), orderId) && intercepted.compareAndSet(false, true)) {
                paymentPaused.countDown();
                assertThat(allowPaymentToContinue.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return record;
        }).when(posStore).requireOrderForUpdate(orderId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> paymentFuture = executor.submit(() -> mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                            .header("Authorization", bearer())
                            .header("Idempotency-Key", "pay-before-update-race")
                            .contentType("application/json")
                            .content("""
                                    {
                                      "paymentMethod": "CARD",
                                      "amount": 30.00,
                                      "transactionRef": "txn-update-race"
                                    }
                                    """))
                    .andReturn().getResponse().getStatus());

            assertThat(paymentPaused.await(5, TimeUnit.SECONDS)).isTrue();
            allowPaymentToContinue.countDown();
            assertThat(paymentFuture.get(5, TimeUnit.SECONDS)).isEqualTo(200);

            mockMvc.perform(patch("/sale-orders/{id}", orderId)
                            .header("Authorization", bearer())
                            .contentType("application/json")
                            .content("""
                                    {
                                      "note": "should fail after payment",
                                      "lines": [
                                        {"productId": 11, "qty": 1.0000}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isConflict());
        } finally {
            allowPaymentToContinue.countDown();
            executor.shutdownNow();
        }

        Integer paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.sale_payment
                WHERE sale_order_id = ?
                """, Integer.class, orderId);
        String productCode = jdbcTemplate.queryForObject("""
                SELECT product_code
                FROM pos.sale_order_line
                WHERE sale_order_id = ?
                  AND line_number = 1
                """, String.class, orderId);
        assertThat(paymentCount).isEqualTo(1);
        assertThat(productCode).isEqualTo("LATTE");
    }

    @Test
    @Tag("security-gap")
    void shouldRejectReadingSessionAndOrderOutsideRouteScopeById() throws Exception {
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

        String inScopeToken = issueToken(
                Set.of("pos.session.read", "pos.order.read"),
                List.of(1L),
                List.of(101L)
        );
        String outOfScopeToken = issueToken(
                Set.of("pos.session.read", "pos.order.read"),
                List.of(999L),
                List.of(999L)
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/pos-sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + inScopeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/pos-sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + outOfScopeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/sale-orders/{id}", orderId)
                        .header("Authorization", "Bearer " + inScopeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/sale-orders/{id}", orderId)
                        .header("Authorization", "Bearer " + outOfScopeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("security-gap")
    void shouldIssueExplicitAudienceForPosDownstreamServiceTokens() throws Exception {
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

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pos-security-gap-payment")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 55.00,
                                  "transactionRef": "txn-security-gap"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());

        FernJwtClaims catalogClaims = jwtService.decode(lastCatalogAuthorization.substring("Bearer ".length()));
        FernJwtClaims inventoryClaims = jwtService.decode(lastInventoryAuthorization.substring("Bearer ".length()));

        assertThat(catalogClaims.issuer()).isEqualTo("pos-service");
        assertThat(catalogClaims.audience()).containsExactly("catalog-service");
        assertThat(inventoryClaims.issuer()).isEqualTo("pos-service");
        assertThat(inventoryClaims.audience()).containsExactly("inventory-service");
    }

    @Test
    void shouldAllowSessionOpenForSystemScopedManager() throws Exception {
        String systemScopedToken = issueToken(
                POS_FULL_ACCESS_PERMISSIONS,
                new ScopeRoots(true, List.of(), List.of()),
                new ScopeRoots(true, List.of(), List.of())
        );

        Long sessionId = openSession(bearer(systemScopedToken), 1L, 101L);

        mockMvc.perform(get("/pos-sessions/{id}", sessionId)
                        .header("Authorization", bearer(systemScopedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));
    }

    @Test
    void shouldAllowRegionScopedManagerWithoutExplicitOutletMembershipAcrossRouteOperations() throws Exception {
        String regionScopedToken = issueToken(
                POS_FULL_ACCESS_PERMISSIONS,
                new ScopeRoots(false, List.of(1L), List.of()),
                new ScopeRoots(false, List.of(1L, 11L), List.of(102L))
        );

        Long sessionId = openSession(bearer(regionScopedToken), 11L, 102L);

        mockMvc.perform(get("/pos-sessions/{id}", sessionId)
                        .header("Authorization", bearer(regionScopedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value(11))
                .andExpect(jsonPath("$.outletId").value(102));

        Long orderId = createSimpleOrder(bearer(regionScopedToken), sessionId, "region-scope-order");

        mockMvc.perform(get("/sale-orders/{id}", orderId)
                        .header("Authorization", bearer(regionScopedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value(11))
                .andExpect(jsonPath("$.outletId").value(102));

        updateOrderToSingleLatte(bearer(regionScopedToken), orderId);

        mockMvc.perform(post("/sale-orders/{id}/cancel", orderId)
                        .header("Authorization", bearer(regionScopedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        closeAndReconcileSession(bearer(regionScopedToken), sessionId);
    }

    @Test
    void shouldAllowOutletOnlyCashierAcrossAllPosEntryPointsWithoutRegionRoot() throws Exception {
        String outletOnlyToken = issueToken(
                POS_FULL_ACCESS_PERMISSIONS,
                new ScopeRoots(false, List.of(), List.of(101L)),
                new ScopeRoots(false, List.of(), List.of(101L))
        );

        Long sessionId = openSession(bearer(outletOnlyToken), 1L, 101L);

        mockMvc.perform(get("/pos-sessions/{id}", sessionId)
                        .header("Authorization", bearer(outletOnlyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));

        Long completedOrderId = createSimpleOrder(bearer(outletOnlyToken), sessionId, "outlet-only-complete");

        mockMvc.perform(get("/sale-orders/{id}", completedOrderId)
                        .header("Authorization", bearer(outletOnlyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(completedOrderId));

        updateOrderToSingleLatte(bearer(outletOnlyToken), completedOrderId);
        addCardPayment(bearer(outletOnlyToken), completedOrderId, "outlet-only-pay-1", "55.00");

        mockMvc.perform(post("/sale-orders/{id}/complete", completedOrderId)
                        .header("Authorization", bearer(outletOnlyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Long cancelledOrderId = createSimpleOrder(bearer(outletOnlyToken), sessionId, "outlet-only-cancel");

        mockMvc.perform(post("/sale-orders/{id}/cancel", cancelledOrderId)
                        .header("Authorization", bearer(outletOnlyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        closeAndReconcileSession(bearer(outletOnlyToken), sessionId);
    }

    @Test
    void shouldAllowSessionOpenWhenOutletScopeMatchesEvenIfRegionRootDiffers() throws Exception {
        String mixedScopeToken = issueToken(
                POS_FULL_ACCESS_PERMISSIONS,
                new ScopeRoots(false, List.of(999L), List.of(101L)),
                new ScopeRoots(false, List.of(999L), List.of(101L))
        );

        Long sessionId = openSession(bearer(mixedScopeToken), 1L, 101L);

        mockMvc.perform(get("/pos-sessions/{id}", sessionId)
                        .header("Authorization", bearer(mixedScopeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(101));
    }

    @Test
    void shouldRejectSessionOpenForEmptyScopePrincipal() throws Exception {
        String emptyScopeToken = issueToken(
                Set.of("pos.session.open"),
                List.of(),
                List.of()
        );

        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", "Bearer " + emptyScopeToken)
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
    void shouldRejectSessionOpenWhenOutletRouteDoesNotMatchRequestedRegion() throws Exception {
        String forgedRouteToken = issueToken(
                Set.of("pos.session.open"),
                List.of(999L),
                List.of(101L)
        );

        mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", "Bearer " + forgedRouteToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 999,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectUpdatingOrderAfterSuccessfulPayment() throws Exception {
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
                                    {"productId": 10, "qty": 2.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-update-block")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 110.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        mockMvc.perform(patch("/sale-orders/{id}", orderId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"productId": 11, "qty": 1.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnServiceUnavailableAndAvoidWritesWhenInventoryReserveTimesOutOnComplete() throws Exception {
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

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-inventory-timeout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 55.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        delayInventoryReservationResponse = true;
        inventoryReservationDelayMillis = 1000L;
        try {
            mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                            .header("Authorization", bearer()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("downstream_unavailable"));
        } finally {
            delayInventoryReservationResponse = false;
        }

        String orderStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, orderId);
        Integer snapshotCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.sale_snapshot
                WHERE sale_order_id = ?
                """, Integer.class, orderId);
        Integer outboxCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.outbox_event
                WHERE aggregate_id = ?
                """, Integer.class, orderId.toString());

        assertThat(orderStatus).isEqualTo("OPEN");
        assertThat(snapshotCount).isZero();
        assertThat(outboxCount).isZero();
        assertThat(inventoryReleaseRequestCount).isZero();
    }

    @Test
    void shouldNotDuplicateCompletionWhenClientRetriesAfterCommit() throws Exception {
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
                                  "orderType": "TAKEAWAY",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-complete-retry")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 55.00,
                                  "transactionRef": "txn-complete-retry"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Integer snapshotCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.sale_snapshot
                WHERE sale_order_id = ?
                """, Integer.class, orderId);
        Integer outboxCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.outbox_event
                WHERE aggregate_id = ?
                  AND event_type = 'pos.sale.completed'
                """, Integer.class, orderId.toString());

        assertThat(snapshotCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void shouldRejectClosingSessionWhileOrderCompletionIsInProgress() throws Exception {
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

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-completing-session-close")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 55.00
                                }
                                """))
                .andExpect(status().isOk());

        delayInventoryReservationResponse = true;
        inventoryReservationDelayMillis = 300L;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> completionFuture = executor.submit(() -> mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                            .header("Authorization", bearer()))
                    .andReturn().getResponse().getStatus());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            String orderStatus = null;
            while (System.nanoTime() < deadline) {
                orderStatus = jdbcTemplate.queryForObject("SELECT status FROM pos.sale_order WHERE id = ?", String.class, orderId);
                if ("COMPLETING".equals(orderStatus)) {
                    break;
                }
                Thread.sleep(25);
            }
            assertThat(orderStatus).isEqualTo("COMPLETING");

            mockMvc.perform(post("/pos-sessions/{id}/close", sessionId)
                            .header("Authorization", bearer()))
                    .andExpect(status().isConflict());

            assertThat(completionFuture.get(5, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            delayInventoryReservationResponse = false;
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectClosingSessionTwice() throws Exception {
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

        mockMvc.perform(post("/pos-sessions/{id}/close", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/pos-sessions/{id}/close", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectReconcilingSessionTwice() throws Exception {
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

        mockMvc.perform(post("/pos-sessions/{id}/close", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/pos-sessions/{id}/reconcile", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "countedCashAmount": 0.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILED"));

        mockMvc.perform(post("/pos-sessions/{id}/reconcile", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "countedCashAmount": 0.00
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReleaseReservationWhenCompletionFailsAfterReserve() throws Exception {
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
                                    {"productId": 10, "qty": 2.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-complete-release")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CASH",
                                  "amount": 110.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        doThrow(new RuntimeException("forced completion failure"))
                .when(posStore)
                .requireOrderForUpdate(orderId);

        mockMvc.perform(post("/sale-orders/{id}/complete", orderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isInternalServerError());

        assertThat(inventoryReleaseRequestCount).isEqualTo(1);
        assertThat(lastReleasedReservationId).isEqualTo(999L);
        assertThat(lastInventoryActorUserId).isEqualTo("1");
        assertThat(lastInventoryAuthorization).isNotBlank().isNotEqualTo(bearer());

        String orderStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, orderId);
        Long outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.outbox_event", Long.class);

        assertThat(orderStatus).isEqualTo("OPEN");
        assertThat(outboxCount).isZero();
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

    @Test
    void shouldReturnInternalErrorAndAvoidWritesWhenOrgServiceIsUnavailable() throws Exception {
        doThrow(new IllegalStateException("org-service is unavailable while resolving outlet 101"))
                .when(posOrgClient)
                .requireOutlet(101L);

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
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.pos_session", Integer.class)).isZero();
    }

    @Test
    void shouldAllowAddingPaymentWhileInventoryDependencyIsUnavailable() throws Exception {
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

        inventoryCircuitBreaker.transitionToOpenState();
        try {
            mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                            .header("Authorization", bearer())
                            .header("Idempotency-Key", "pay-while-inventory-open")
                            .contentType("application/json")
                            .content("""
                                    {
                                      "paymentMethod": "CASH",
                                      "amount": 30.00
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        } finally {
            inventoryCircuitBreaker.reset();
        }

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pos.sale_payment
                WHERE sale_order_id = ?
                """, Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT payment_status
                FROM pos.sale_order
                WHERE id = ?
                """, String.class, orderId)).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    void shouldRoundFractionalPricesAndTaxesHalfUp() throws Exception {
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
                                    {"productId": 12, "qty": 3.0000}
                                  ]
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(30.02))
                .andExpect(jsonPath("$.taxAmount").value(2.33))
                .andExpect(jsonPath("$.totalAmount").value(32.35))
                .andExpect(jsonPath("$.lines[0].taxAmount").value(2.33))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(32.35))
                .andReturn().getResponse().getContentAsString();
        Long orderId = readId(orderJson);

        BigDecimal storedSubtotal = jdbcTemplate.queryForObject("""
                SELECT subtotal
                FROM pos.sale_order
                WHERE id = ?
                """, BigDecimal.class, orderId);
        BigDecimal storedTax = jdbcTemplate.queryForObject("""
                SELECT tax_amount
                FROM pos.sale_order
                WHERE id = ?
                """, BigDecimal.class, orderId);
        BigDecimal storedTotal = jdbcTemplate.queryForObject("""
                SELECT total_amount
                FROM pos.sale_order
                WHERE id = ?
                """, BigDecimal.class, orderId);

        assertThat(storedSubtotal).isEqualByComparingTo("30.02");
        assertThat(storedTax).isEqualByComparingTo("2.33");
        assertThat(storedTotal).isEqualByComparingTo("32.35");
    }

    @Test
    void shouldPublishFailedPaymentAlertWithCorrelationId() throws Exception {
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

        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "pay-failed-correlation")
                        .header(CorrelationId.HEADER, "corr-pay-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 55.00,
                                  "status": "FAILED",
                                  "transactionRef": "txn-failed-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));

        verify(operationalAlertPublisher).publish(
                eq("PAYMENT_FAILED"),
                eq("MEDIUM"),
                contains("Sale payment failed for order " + orderId),
                eq("corr-pay-1"),
                eq(1L),
                eq(101L),
                eq("SALE_ORDER"),
                eq(orderId.toString()),
                anyMap()
        );
    }

    @Test
    void shouldNotRepublishOutboxEventAfterRetryLimitIsReached() {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO pos.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, retry_count, created_at
                ) VALUES (
                    CAST(? AS uuid), 'SALE_ORDER', '10', 'pos.sale.completed', '101', CAST(? AS jsonb), 'PENDING', 3, CURRENT_TIMESTAMP
                )
                """, eventId, "{\"saleOrderId\":10,\"outletId\":101}");

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        PosOutboxProperties properties = new PosOutboxProperties();
        properties.setMaxAttempts(3);
        properties.setReclaimAfter(Duration.ofMinutes(1));
        PosOutboxPublisher publisher = new PosOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );

        publisher.publishPending();

        verify(kafkaTemplate, times(0)).send(anyString(), anyString(), anyString());
        String status = jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.outbox_event
                WHERE id = CAST(? AS uuid)
                """, String.class, eventId);
        Integer retryCount = jdbcTemplate.queryForObject("""
                SELECT retry_count
                FROM pos.outbox_event
                WHERE id = CAST(? AS uuid)
                """, Integer.class, eventId);

        assertThat(status).isEqualTo("PENDING");
        assertThat(retryCount).isEqualTo(3);
    }

    @Test
    void shouldNotClaimSameOutboxEventAcrossConcurrentPublishers() throws Exception {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO pos.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(? AS uuid), 'SALE_ORDER', '10', 'pos.sale.completed', '10', CAST(? AS jsonb), 'PENDING', CURRENT_TIMESTAMP
                )
                """, eventId, "{\"id\":10}");

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<Object> firstSend = new CompletableFuture<>();
        CountDownLatch sendStarted = new CountDownLatch(1);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            sendStarted.countDown();
            return firstSend;
        });

        PosOutboxProperties properties = new PosOutboxProperties();
        properties.setMaxAttempts(3);
        properties.setReclaimAfter(Duration.ofMinutes(1));
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC);
        PosOutboxPublisher firstPublisher = new PosOutboxPublisher(
                namedJdbcTemplate,
                kafkaTemplate,
                properties,
                fixedClock,
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );
        PosOutboxPublisher secondPublisher = new PosOutboxPublisher(
                namedJdbcTemplate,
                kafkaTemplate,
                properties,
                fixedClock,
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> firstRun = executor.submit(firstPublisher::publishPending);
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            secondPublisher.publishPending();
            verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());

            firstSend.complete(null);
            firstRun.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        String status = jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.outbox_event
                WHERE id = CAST(? AS uuid)
                """, String.class, eventId);
        assertThat(status).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldReclaimStaleInProgressSaleCompletedOutboxEvent() {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO pos.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, retry_count, created_at, last_attempt_at
                ) VALUES (
                    CAST(? AS uuid), 'SALE_ORDER', '10', 'pos.sale.completed', '101', CAST(? AS jsonb),
                    'IN_PROGRESS', 1, TIMESTAMPTZ '2026-03-27T09:00:00Z', TIMESTAMPTZ '2026-03-27T09:30:00Z'
                )
                """, eventId, "{\"saleOrderId\":10,\"outletId\":101}");

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq("pos.sale.completed"), eq("101"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        PosOutboxProperties properties = new PosOutboxProperties();
        properties.setMaxAttempts(3);
        properties.setReclaimAfter(Duration.ofMinutes(1));
        PosOutboxPublisher publisher = new PosOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                properties,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );

        publisher.publishPending();

        verify(kafkaTemplate).send(eq("pos.sale.completed"), eq("101"), anyString());
        String status = jdbcTemplate.queryForObject("""
                SELECT status
                FROM pos.outbox_event
                WHERE id = CAST(? AS uuid)
                """, String.class, eventId);
        Integer retryCount = jdbcTemplate.queryForObject("""
                SELECT retry_count
                FROM pos.outbox_event
                WHERE id = CAST(? AS uuid)
                """, Integer.class, eventId);

        assertThat(status).isEqualTo("PUBLISHED");
        assertThat(retryCount).isEqualTo(1);
    }

    private Long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String concurrentOrderCreate(
            CountDownLatch start,
            CountDownLatch ready,
            Long sessionId,
            String note
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post("/sale-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "DINE_IN",
                                  "note": "%s",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId, note)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Integer concurrentPaymentStatus(
            CountDownLatch start,
            CountDownLatch ready,
            Long orderId,
            String idempotencyKey,
            String accessToken,
            String transactionRef
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": 30.00,
                                  "transactionRef": "%s"
                                }
                                """.formatted(transactionRef)))
                .andReturn().getResponse().getStatus();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private Long openSession(String authorizationHeader, Long regionId, Long outletId) throws Exception {
        String sessionJson = mockMvc.perform(post("/pos-sessions")
                        .header("Authorization", authorizationHeader)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": %d,
                                  "outletId": %d,
                                  "currencyCode": "VND",
                                  "businessDate": "2026-03-27"
                                }
                                """.formatted(regionId, outletId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(sessionJson);
    }

    private Long createSimpleOrder(String authorizationHeader, Long sessionId, String note) throws Exception {
        String orderJson = mockMvc.perform(post("/sale-orders")
                        .header("Authorization", authorizationHeader)
                        .contentType("application/json")
                        .content("""
                                {
                                  "posSessionId": %d,
                                  "orderType": "DINE_IN",
                                  "note": "%s",
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """.formatted(sessionId, note)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(orderJson);
    }

    private void updateOrderToSingleLatte(String authorizationHeader, Long orderId) throws Exception {
        mockMvc.perform(patch("/sale-orders/{id}", orderId)
                        .header("Authorization", authorizationHeader)
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"productId": 10, "qty": 1.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    private void addCardPayment(String authorizationHeader, Long orderId, String idempotencyKey, String amount) throws Exception {
        mockMvc.perform(post("/sale-orders/{id}/payments", orderId)
                        .header("Authorization", authorizationHeader)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "paymentMethod": "CARD",
                                  "amount": %s,
                                  "transactionRef": "%s"
                                }
                                """.formatted(amount, idempotencyKey.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    private void closeAndReconcileSession(String authorizationHeader, Long sessionId) throws Exception {
        mockMvc.perform(post("/pos-sessions/{id}/close", sessionId)
                        .header("Authorization", authorizationHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(post("/pos-sessions/{id}/reconcile", sessionId)
                        .header("Authorization", authorizationHeader)
                        .contentType("application/json")
                        .content("""
                                {
                                  "countedCashAmount": 0.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILED"));
    }

    private String issueToken(Set<String> permissions, List<Long> regions, List<Long> outlets) {
        ScopeRoots scopeRoots = new ScopeRoots(false, regions, outlets);
        return issueToken(1L, "pos-tester", permissions, scopeRoots, scopeRoots);
    }

    private String issueToken(Long userId, String username, Set<String> permissions, List<Long> regions, List<Long> outlets) {
        ScopeRoots scopeRoots = new ScopeRoots(false, regions, outlets);
        return issueToken(userId, username, permissions, scopeRoots, scopeRoots);
    }

    private String issueToken(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return issueToken(1L, "pos-tester", permissions, scopeRoots, accessibleScope);
    }

    private String issueToken(
            Long userId,
            String username,
            Set<String> permissions,
            ScopeRoots scopeRoots,
            ScopeRoots accessibleScope
    ) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        return jwtService.encode(new FernJwtClaims(
                userId,
                username,
                Set.of("outlet_manager"),
                permissions,
                scopeRoots,
                accessibleScope,
                1L,
                1L,
                "pos-test-jti-" + userId + "-" + permissions.hashCode() + "-" + scopeRoots.hashCode() + "-" + accessibleScope.hashCode(),
                Instant.now(),
                Instant.now().plusSeconds(900),
                FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("pos-service")
        ), jwtService.accessTokenTtl());
    }
}
