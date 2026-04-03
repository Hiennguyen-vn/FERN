package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientProperties;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

class PosInventoryClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldReserveInventoryAndSendTypedBodyAndHeaders() throws Exception {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        server.createContext("/internal/inventory/sale-reservations", exchange -> {
            capturedHeaders.set(new Headers(exchange.getRequestHeaders()));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"reservationId":77,"expiresAt":"2026-04-03T10:15:30Z"}
                    """);
        });

        FernJwtService jwtService = jwtService();
        PosInventoryClient client = new PosInventoryClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("pos-inventory-test"),
                clientSpec(),
                tokenSupport(jwtService),
                downstreamClientFactory(),
                errorMapper()
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-pos-inventory");
        SaleReservationResponse response;
        try {
            response = client.reserveInventory(
                    actorPrincipal(),
                    101L,
                    LocalDate.of(2026, 4, 3),
                    501L,
                    java.util.List.of(new RecipeUsageItem(200L, "MILK", "Fresh Milk", "ML", new BigDecimal("180")))
            );
        } finally {
            MDC.clear();
        }

        assertThat(response).isEqualTo(new SaleReservationResponse(77L, Instant.parse("2026-04-03T10:15:30Z")));
        FernJwtClaims claims = bearerClaims(jwtService, capturedHeaders.get());
        assertThat(claims.issuer()).isEqualTo("pos-service");
        assertThat(claims.audience()).containsExactly("inventory-service");
        assertThat(claims.permissions()).contains(PermissionCodes.INVENTORY_INTERNAL_RESERVE);
        assertThat(capturedHeaders.get().getFirst(CorrelationId.HEADER)).isEqualTo("corr-pos-inventory");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USER_ID)).isEqualTo("51");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USERNAME)).isEqualTo("cashier-a");

        JsonNode request = objectMapper.readTree(capturedBody.get());
        assertThat(request.path("outletId").asLong()).isEqualTo(101L);
        assertThat(asBusinessDate(request.path("businessDate"))).isEqualTo("2026-04-03");
        assertThat(request.path("sourceOrderId").asLong()).isEqualTo(501L);
        assertThat(request.path("usageItems").size()).isEqualTo(1);
        assertThat(request.path("usageItems").get(0).path("ingredientCode").asText()).isEqualTo("MILK");
        assertThat(request.path("usageItems").get(0).path("qty").decimalValue()).isEqualByComparingTo("180");
    }

    @Test
    void shouldMapReservationReleaseFailuresToDownstreamUnavailable() {
        server.createContext("/internal/inventory/sale-reservations/77/cancel", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        PosInventoryClient client = new PosInventoryClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("pos-inventory-test"),
                clientSpec(),
                tokenSupport(jwtService()),
                downstreamClientFactory(),
                errorMapper()
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-pos-inventory");
        try {
            assertThatThrownBy(() -> client.releaseInventoryReservation(actorPrincipal(), 77L))
                    .isInstanceOf(DownstreamUnavailableException.class)
                    .hasMessage("inventory-service is unavailable");
        } finally {
            MDC.clear();
        }
    }

    private FernPrincipal actorPrincipal() {
        return new FernPrincipal(
                51L,
                "cashier-a",
                Set.of("STAFF"),
                Set.of(),
                ScopeRoots.empty(),
                1L,
                1L,
                "pos-inventory-test-jti"
        );
    }

    private FernDownstreamClientSpec clientSpec() {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return new FernDownstreamClientSpec(PosServiceNames.POS_SERVICE, PosServiceNames.INVENTORY_SERVICE, "inventory", properties);
    }

    private FernDownstreamClientFactory downstreamClientFactory() {
        return new FernDownstreamClientFactory(new SimpleMeterRegistry());
    }

    private FernDownstreamErrorMapper errorMapper() {
        return new FernDownstreamErrorMapper(objectMapper);
    }

    private FernJwtClaims bearerClaims(FernJwtService jwtService, Headers headers) {
        String bearer = headers.getFirst("Authorization");
        assertThat(bearer).startsWith("Bearer ");
        return jwtService.decode(bearer.substring("Bearer ".length()));
    }

    private FernJwtService jwtService() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("pos-inventory-client-test-secret-012345678901234567");
        properties.setAllowInsecureDefaultSecret(true);
        return new FernJwtService(properties, Clock.systemUTC());
    }

    @SuppressWarnings("unchecked")
    private FernServiceTokenSupport tokenSupport(FernJwtService jwtService) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("0");
        return new FernServiceTokenSupport(jwtService, Clock.systemUTC(), redisTemplate);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String asBusinessDate(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray() && node.size() == 3) {
            return "%04d-%02d-%02d".formatted(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
        }
        return node.asText();
    }
}
