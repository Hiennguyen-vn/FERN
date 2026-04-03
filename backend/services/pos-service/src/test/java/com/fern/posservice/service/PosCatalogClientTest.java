package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

class PosCatalogClientTest {
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
    void shouldFetchMenuAndSendInternalHeaders() {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        server.createContext("/internal/catalog/menu", exchange -> respond(exchange, 200, """
                {"outletId":101,"businessDate":"2026-04-03","items":[{"productId":1,"productCode":"LATTE","productName":"Iced Latte","categoryCode":"COFFEE","currencyCode":"VND","priceValue":49000,"taxPercent":8}]}
                """, capturedHeaders));

        FernJwtService jwtService = jwtService();
        PosCatalogClient client = new PosCatalogClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("pos-catalog-test"),
                clientSpec(),
                tokenSupport(jwtService),
                downstreamClientFactory(),
                errorMapper(),
                60
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-pos-catalog");
        MenuResponse response;
        try {
            response = client.fetchMenu(actorPrincipal(), 101L, LocalDate.of(2026, 4, 3));
        } finally {
            MDC.clear();
        }

        assertThat(response.outletId()).isEqualTo(101L);
        assertThat(response.businessDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(response.items()).containsExactly(new MenuItem(
                1L,
                "LATTE",
                "Iced Latte",
                "COFFEE",
                "VND",
                new BigDecimal("49000"),
                new BigDecimal("8")
        ));
        FernJwtClaims claims = bearerClaims(jwtService, capturedHeaders.get());
        assertThat(claims.issuer()).isEqualTo("pos-service");
        assertThat(claims.audience()).containsExactly("catalog-service");
        assertThat(claims.permissions()).contains(PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        assertThat(capturedHeaders.get().getFirst(CorrelationId.HEADER)).isEqualTo("corr-pos-catalog");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USER_ID)).isEqualTo("51");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USERNAME)).isEqualTo("cashier-a");
    }

    @Test
    void shouldMapCatalogFailuresToDownstreamUnavailable() {
        server.createContext("/internal/catalog/menu", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        PosCatalogClient client = new PosCatalogClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("pos-catalog-test"),
                clientSpec(),
                tokenSupport(jwtService()),
                downstreamClientFactory(),
                errorMapper(),
                60
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-pos-catalog");
        try {
            assertThatThrownBy(() -> client.fetchMenu(actorPrincipal(), 101L, LocalDate.of(2026, 4, 3)))
                    .isInstanceOf(DownstreamUnavailableException.class)
                    .hasMessage("catalog-service is unavailable");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldResolveRecipesUsingTypedContract() {
        server.createContext("/internal/catalog/recipe-resolutions", exchange -> respond(exchange, 200, """
                [{"productId":1,"recipeId":11,"recipeVersionId":21,"recipeCode":"LATTE-BASE","versionNo":"v3","effectiveFrom":"2026-04-01","effectiveTo":null,"ingredients":[{"ingredientId":200,"ingredientCode":"MILK","ingredientName":"Fresh Milk","uomCode":"ML","qty":180,"sortOrder":1}]}]
                """, new AtomicReference<>()));

        PosCatalogClient client = new PosCatalogClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("pos-catalog-test"),
                clientSpec(),
                tokenSupport(jwtService()),
                downstreamClientFactory(),
                errorMapper(),
                60
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-pos-catalog");
        List<RecipeSnapshot> response;
        try {
            response = client.resolveRecipes(actorPrincipal(), List.of(1L), LocalDate.of(2026, 4, 3));
        } finally {
            MDC.clear();
        }

        assertThat(response).containsExactly(new RecipeSnapshot(
                1L,
                11L,
                21L,
                "LATTE-BASE",
                "v3",
                LocalDate.of(2026, 4, 1),
                null,
                List.of(new RecipeIngredient(200L, "MILK", "Fresh Milk", "ML", new BigDecimal("180"), 1))
        ));
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
                "pos-catalog-test-jti"
        );
    }

    private FernDownstreamClientSpec clientSpec() {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return new FernDownstreamClientSpec(PosServiceNames.POS_SERVICE, PosServiceNames.CATALOG_SERVICE, "catalog", properties);
    }

    private FernDownstreamClientFactory downstreamClientFactory() {
        return new FernDownstreamClientFactory(new SimpleMeterRegistry());
    }

    private FernDownstreamErrorMapper errorMapper() {
        return new FernDownstreamErrorMapper(new ObjectMapper().findAndRegisterModules());
    }

    private FernJwtClaims bearerClaims(FernJwtService jwtService, Headers headers) {
        String bearer = headers.getFirst("Authorization");
        assertThat(bearer).startsWith("Bearer ");
        return jwtService.decode(bearer.substring("Bearer ".length()));
    }

    private FernJwtService jwtService() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("pos-catalog-client-test-secret-012345678901234567890");
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

    private void respond(HttpExchange exchange, int status, String body, AtomicReference<Headers> capturedHeaders) throws IOException {
        capturedHeaders.set(new Headers(exchange.getRequestHeaders()));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
