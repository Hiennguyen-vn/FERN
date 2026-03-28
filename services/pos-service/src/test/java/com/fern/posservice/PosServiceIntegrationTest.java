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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.fern.posservice.service.PosOutboxPublisher;
import com.fern.posservice.service.PosStore;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
    private static HttpServer catalogServer;
    private static HttpServer inventoryServer;
    private static HttpServer orgServer;
    private static volatile String lastCatalogAuthorization;
    private static volatile String lastCatalogActorUserId;
    private static volatile String lastInventoryAuthorization;
    private static volatile String lastInventoryActorUserId;
    private static volatile int inventoryReleaseRequestCount;
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
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.clients.catalog.base-url", () -> "http://localhost:" + catalogServer.getAddress().getPort());
        registry.add("fern.clients.catalog.connect-timeout", () -> "500ms");
        registry.add("fern.clients.catalog.read-timeout", () -> "500ms");
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost:" + inventoryServer.getAddress().getPort());
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
        lastReleasedReservationId = null;
        delayCatalogMenuResponse = false;
        recipeBatchRequestCount = 0;
        catalogCircuitBreaker.reset();
        inventoryCircuitBreaker.reset();
        reset(posStore, operationalAlertPublisher);
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
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
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Unsupported payment status"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pos.sale_payment WHERE sale_order_id = ?",
                Integer.class,
                orderId
        )).isZero();
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
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(readId(secondResponse)).isEqualTo(firstSessionId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pos.pos_session", Integer.class)).isEqualTo(1);
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
        properties.setAllowInsecureDefaultSecret(true);
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
