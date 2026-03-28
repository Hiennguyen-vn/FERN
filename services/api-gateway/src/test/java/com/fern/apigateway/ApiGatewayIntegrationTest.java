package com.fern.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayIntegrationTest {
    private static HttpServer iamServer;
    private static HttpServer orgServer;
    private static HttpServer catalogServer;
    private static HttpServer auditServer;
    private static HttpServer posServer;
    private static HttpServer inventoryServer;
    private static HttpServer procurementServer;

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ensureServersStarted();
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> true);
        registry.add("fern.routes.iam", () -> "http://localhost:" + iamServer.getAddress().getPort());
        registry.add("fern.routes.org", () -> "http://localhost:" + orgServer.getAddress().getPort());
        registry.add("fern.routes.catalog", () -> "http://localhost:" + catalogServer.getAddress().getPort());
        registry.add("fern.routes.audit", () -> "http://localhost:" + auditServer.getAddress().getPort());
        registry.add("fern.routes.pos", () -> "http://localhost:" + posServer.getAddress().getPort());
        registry.add("fern.routes.inventory", () -> "http://localhost:" + inventoryServer.getAddress().getPort());
        registry.add("fern.routes.procurement", () -> "http://localhost:" + procurementServer.getAddress().getPort());
    }

    private static void ensureServersStarted() {
        if (iamServer != null && orgServer != null && catalogServer != null && auditServer != null
                && posServer != null && inventoryServer != null && procurementServer != null) {
            return;
        }
        try {
            startMockServers();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start mock gateway backends", exception);
        }
    }

    @BeforeAll
    static void startMockServers() throws IOException {
        iamServer = HttpServer.create(new InetSocketAddress(0), 0);
        iamServer.createContext("/auth/login", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        iamServer.start();

        orgServer = HttpServer.create(new InetSocketAddress(0), 0);
        orgServer.createContext("/regions/1", exchange -> {
            String response = "{\"path\":\"" + exchange.getRequestURI().getPath() + "\",\"correlationId\":\"" +
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id") + "\"}";
            byte[] bytes = response.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        orgServer.start();

        catalogServer = HttpServer.create(new InetSocketAddress(0), 0);
        catalogServer.createContext("/internal/catalog/menu", exchange -> {
            byte[] bytes = "{\"items\":[]}".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        catalogServer.start();

        auditServer = HttpServer.create(new InetSocketAddress(0), 0);
        auditServer.createContext("/audit/events", exchange -> {
            byte[] bytes = "{\"items\":[]}".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        auditServer.start();

        posServer = HttpServer.create(new InetSocketAddress(0), 0);
        posServer.createContext("/pos-sessions/1", exchange -> {
            byte[] bytes = "{\"id\":1,\"status\":\"OPEN\"}".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        posServer.start();

        inventoryServer = HttpServer.create(new InetSocketAddress(0), 0);
        inventoryServer.createContext("/stock-balances", exchange -> {
            byte[] bytes = "[{\"outletId\":101,\"ingredientId\":200}]".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        inventoryServer.start();

        procurementServer = HttpServer.create(new InetSocketAddress(0), 0);
        procurementServer.createContext("/suppliers", exchange -> {
            byte[] bytes = "[{\"id\":1,\"supplierCode\":\"SUP-001\"}]".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        procurementServer.start();
    }

    @AfterAll
    static void stopServers() {
        if (iamServer != null) {
            iamServer.stop(0);
        }
        if (orgServer != null) {
            orgServer.stop(0);
        }
        if (catalogServer != null) {
            catalogServer.stop(0);
        }
        if (auditServer != null) {
            auditServer.stop(0);
        }
        if (posServer != null) {
            posServer.stop(0);
        }
        if (inventoryServer != null) {
            inventoryServer.stop(0);
        }
        if (procurementServer != null) {
            procurementServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
    }

    @Test
    void shouldAllowPublicLoginWithoutToken() {
        webTestClient.post()
                .uri("/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void shouldRejectProtectedRouteWithoutToken() {
        webTestClient.get()
                .uri("/regions/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldForwardProtectedRequestWithValidToken() throws Exception {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("org.region.read"),
                new ScopeRoots(java.util.List.of(1L), java.util.List.of()),
                1L,
                1L,
                "gateway-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        String body = webTestClient.get()
                .uri("/regions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("path").asText()).isEqualTo("/regions/1");
        assertThat(response.get("correlationId").asText()).isNotBlank();
    }

    @Test
    void shouldRejectBlacklistedToken() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("org.region.read"),
                new ScopeRoots(java.util.List.of(1L), java.util.List.of()),
                1L,
                1L,
                "blacklisted-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");
        redisTemplate.opsForValue().set("fern:iam:blacklist:blacklisted-jti", "1");

        webTestClient.get()
                .uri("/regions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldNotExposeInternalCatalogRoutesThroughGateway() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("catalog.internal.resolve"),
                new ScopeRoots(true, java.util.List.of(), java.util.List.of()),
                1L,
                1L,
                "internal-route-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        webTestClient.get()
                .uri("/internal/catalog/menu?outletId=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRouteAuditRequests() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("audit.read"),
                new ScopeRoots(java.util.List.of(1L), java.util.List.of()),
                1L,
                1L,
                "audit-route-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        String body = webTestClient.get()
                .uri("/audit/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("\"items\"");
    }

    @Test
    void shouldRoutePosInventoryAndProcurementRequests() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("pos.session.read", "inventory.balance.read", "procurement.supplier.read"),
                new ScopeRoots(java.util.List.of(1L), java.util.List.of(101L)),
                1L,
                1L,
                "phase3-routes-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        webTestClient.get()
                .uri("/pos-sessions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"status\":\"OPEN\""));

        webTestClient.get()
                .uri("/stock-balances?outletId=101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"ingredientId\":200"));

        webTestClient.get()
                .uri("/suppliers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"supplierCode\":\"SUP-001\""));
    }
}
