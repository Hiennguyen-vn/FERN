package com.fern.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.apigateway.outbox.GatewayOutboxPublisher;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class ApiGatewayIntegrationTest {
    private static final String TEST_SECRET = "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv";
    private static HttpServer iamServer;
    private static HttpServer orgServer;
    private static HttpServer catalogServer;
    private static HttpServer auditServer;
    private static HttpServer posServer;
    private static HttpServer inventoryServer;
    private static HttpServer procurementServer;
    private static volatile String lastOrgAuthorizationHeader;

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GatewayOutboxPublisher gatewayOutboxPublisher;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ensureServersStarted();
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("gateway"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
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
            lastOrgAuthorizationHeader = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
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
        catalogServer.createContext("/catalog/promotions", exchange -> {
            byte[] bytes = "[{\"id\":1,\"code\":\"PROMO-GW\",\"status\":\"ACTIVE\"}]".getBytes();
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
        jdbcTemplate.execute("TRUNCATE TABLE gateway.outbox_event");
        lastOrgAuthorizationHeader = null;
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

        waitForRows("REQUEST_TRACE", 1);
        waitForRows("SECURITY_EVENT", 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gateway.outbox_event WHERE aggregate_type = 'SECURITY_EVENT' AND event_type = 'audit.security'",
                Integer.class
        )).isEqualTo(1);
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM gateway.outbox_event WHERE aggregate_type = 'SECURITY_EVENT' LIMIT 1",
                String.class
        );
        assertThat(payload).contains("gateway.auth.missing_bearer_token");
    }

    @Test
    void shouldExposeUiActionHubForAuthenticatedUsers() {
        String token = gatewayToken(
                "bootstrap-admin",
                Set.of("iam.user.read", "finance.payroll.read", "hr.attendance.review"),
                1L,
                1L,
                UUID.randomUUID().toString()
        );

        webTestClient.get()
                .uri("/ui/action-hub")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(body -> {
                    assertThat(body.at("/persona").asText()).isEqualTo("system_admin");
                    assertThat(body.at("/scopeSummary/title").asText()).isEqualTo("Scoped operating context");
                    assertThat(body.at("/modules/0/href").asText()).isEqualTo("/home");
                    assertThat(body.toString()).contains("Prepare payroll draft");
                    assertThat(body.toString()).contains("Review payroll approvals");
                });
    }

    @Test
    void shouldExposeUiShellContextForAuthenticatedUsers() {
        String token = gatewayToken(
                "bootstrap-admin",
                Set.of("iam.user.read"),
                1L,
                1L,
                UUID.randomUUID().toString()
        );

        webTestClient.get()
                .uri("/ui/shell-context")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .value(body -> {
                    assertThat(body.at("/principalLabel").asText()).isEqualTo("bootstrap-admin");
                    assertThat(body.at("/roleLabel").asText()).isEqualTo("Bootstrap Admin");
                    assertThat(body.at("/availableRegions/0/label").asText()).isEqualTo("Region #1");
                });
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
        assertThat(lastOrgAuthorizationHeader).startsWith("Bearer ").isNotEqualTo("Bearer " + token);
        FernJwtClaims relayedClaims = jwtService.decode(lastOrgAuthorizationHeader.substring(7));
        assertThat(relayedClaims.issuer()).isEqualTo(FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER);
        assertThat(relayedClaims.audience()).containsExactly("org-service");
        assertThat(relayedClaims.username()).isEqualTo("bootstrap-admin");
        assertThat(relayedClaims.principalType()).isEqualTo(com.fern.platform.common.FernPrincipalType.USER);
        assertThat(relayedClaims.jti()).isEqualTo("gateway-jti");

        waitForRows("REQUEST_TRACE", 1);
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM gateway.outbox_event WHERE aggregate_type = 'REQUEST_TRACE' LIMIT 1",
                String.class
        );
        assertThat(payload).contains("request.trace.recorded");
    }

    @Test
    void shouldRejectBlacklistedToken() {
        String token = gatewayToken("bootstrap-admin", Set.of("org.region.read"), 1L, 1L, "blacklisted-jti");

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
    void shouldRejectStalePolicyTokenAndPublishSecurityEvent() {
        String token = gatewayToken("stale-policy-user", Set.of("org.region.read"), 1L, 1L, "stale-policy-jti");

        redisTemplate.opsForValue().set("fern:versions:policy", "2");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        webTestClient.get()
                .uri("/regions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        waitForRows("SECURITY_EVENT", 1);
        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM gateway.outbox_event
                WHERE aggregate_type = 'SECURITY_EVENT'
                ORDER BY created_at DESC
                LIMIT 1
                """, String.class);
        assertThat(payload)
                .contains("gateway.auth.token_rejected")
                .contains("stale-policy-user")
                .contains("/regions/1");
    }

    @Test
    void shouldRejectStaleScopeTokenAndPublishSecurityEvent() {
        String token = gatewayToken("stale-scope-user", Set.of("org.region.read"), 3L, 1L, "stale-scope-jti");

        redisTemplate.opsForValue().set("fern:versions:policy", "3");
        redisTemplate.opsForValue().set("fern:versions:scope", "2");

        webTestClient.get()
                .uri("/regions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        waitForRows("SECURITY_EVENT", 1);
        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM gateway.outbox_event
                WHERE aggregate_type = 'SECURITY_EVENT'
                ORDER BY created_at DESC
                LIMIT 1
                """, String.class);
        assertThat(payload)
                .contains("gateway.auth.token_rejected")
                .contains("stale-scope-user")
                .contains("/regions/1");
    }

    @Test
    @Tag("security-gap")
    void shouldRejectGatewayTokenMissingIssuer() {
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        webTestClient.get()
                .uri("/regions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawGatewayToken(null, Set.of("api-gateway"), true, false))
                .exchange()
                .expectStatus().isUnauthorized();

        waitForRows("SECURITY_EVENT", 1);
        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM gateway.outbox_event
                WHERE aggregate_type = 'SECURITY_EVENT'
                ORDER BY created_at DESC
                LIMIT 1
                """, String.class);
        assertThat(payload)
                .contains("gateway.auth.invalid_bearer_token")
                .contains("Invalid bearer token")
                .contains("/regions/1");
    }

    @Test
    @Tag("security-gap")
    void shouldRejectGatewayTokenMissingAudience() {
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        webTestClient.get()
                .uri("/regions/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawGatewayToken(FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER, Set.of(), false, true))
                .exchange()
                .expectStatus().isUnauthorized();

        waitForRows("SECURITY_EVENT", 1);
        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM gateway.outbox_event
                WHERE aggregate_type = 'SECURITY_EVENT'
                ORDER BY created_at DESC
                LIMIT 1
                """, String.class);
        assertThat(payload)
                .contains("gateway.auth.invalid_bearer_token")
                .contains("Invalid bearer token")
                .contains("/regions/1");
    }

    @Test
    void shouldNotExposeInternalPathsBeforeAuth() {
        webTestClient.get()
                .uri("/internal/catalog/menu?outletId=1")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post()
                .uri("/internal/hr/effective-contracts")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/internal/inventory/sale-reservations")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/internal/scopes/expand")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldNotExposeInternalPathsEvenWithValidToken() {
        String token = gatewayToken("bootstrap-admin", Set.of("catalog.internal.resolve"), 1L, 1L, "internal-block-jti");
        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        webTestClient.get()
                .uri("/internal/catalog/menu?outletId=1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRouteCatalogPromotionsRequest() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("catalog.promotion.read"),
                new ScopeRoots(true, java.util.List.of(), java.util.List.of()),
                1L,
                1L,
                "promotions-route-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        redisTemplate.opsForValue().set("fern:versions:policy", "1");
        redisTemplate.opsForValue().set("fern:versions:scope", "1");

        String body = webTestClient.get()
                .uri("/catalog/promotions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("PROMO-GW");
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

    @Test
    void shouldPublishGatewayOutboxRowAndMarkPublished() {
        insertOutboxRow(
                "00000000-0000-0000-0000-000000000011",
                "REQUEST_TRACE",
                "trace-11",
                "request.trace",
                "trace-11",
                "{\"requestId\":\"trace-11\"}",
                "PENDING",
                0
        );

        gatewayOutboxPublisher.publishPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gateway.outbox_event WHERE aggregate_id = 'trace-11'",
                String.class
        )).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldMarkGatewayOutboxRowFailedAfterMaxAttempts() {
        insertOutboxRow(
                "00000000-0000-0000-0000-000000000012",
                "SECURITY_EVENT",
                "security-12",
                "audit.security",
                "security-12",
                "{\"eventId\":\"security-12\"}",
                "PENDING",
                4
        );
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        gatewayOutboxPublisher.publishPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gateway.outbox_event WHERE aggregate_id = 'security-12'",
                String.class
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT retry_count FROM gateway.outbox_event WHERE aggregate_id = 'security-12'",
                Integer.class
        )).isEqualTo(5);
    }

    @Test
    void shouldClaimGatewayOutboxRowOnlyOnceAcrossConcurrentPublishers() throws Exception {
        insertOutboxRow(
                "00000000-0000-0000-0000-000000000013",
                "REQUEST_TRACE",
                "trace-concurrent",
                "request.trace",
                "trace-concurrent",
                "{\"requestId\":\"trace-concurrent\"}",
                "PENDING",
                0
        );
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    gatewayOutboxPublisher.publishPending();
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        verify(kafkaTemplate, timeout(1000).times(1))
                .send(org.mockito.ArgumentMatchers.eq("request.trace"), org.mockito.ArgumentMatchers.eq("trace-concurrent"), anyString());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gateway.outbox_event WHERE aggregate_id = 'trace-concurrent'",
                String.class
        )).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldReclaimStaleInProgressGatewayOutboxRow() {
        insertOutboxRow(
                "00000000-0000-0000-0000-000000000014",
                "REQUEST_TRACE",
                "trace-stale",
                "request.trace",
                "trace-stale",
                "{\"requestId\":\"trace-stale\"}",
                "IN_PROGRESS",
                1
        );
        jdbcTemplate.update("""
                UPDATE gateway.outbox_event
                SET last_attempt_at = TIMESTAMPTZ '2026-03-27T11:57:00Z'
                WHERE aggregate_id = 'trace-stale'
                """);

        gatewayOutboxPublisher.publishPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gateway.outbox_event WHERE aggregate_id = 'trace-stale'",
                String.class
        )).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldNotReclaimFreshInProgressGatewayOutboxRow() {
        insertOutboxRow(
                "00000000-0000-0000-0000-000000000015",
                "REQUEST_TRACE",
                "trace-fresh",
                "request.trace",
                "trace-fresh",
                "{\"requestId\":\"trace-fresh\"}",
                "IN_PROGRESS",
                1
        );
        jdbcTemplate.update("""
                UPDATE gateway.outbox_event
                SET last_attempt_at = CURRENT_TIMESTAMP
                WHERE aggregate_id = 'trace-fresh'
                """);

        gatewayOutboxPublisher.publishPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gateway.outbox_event WHERE aggregate_id = 'trace-fresh'",
                String.class
        )).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldNotReclaimTerminalFailedGatewayOutboxRow() {
        insertOutboxRow(
                "00000000-0000-0000-0000-000000000016",
                "SECURITY_EVENT",
                "security-terminal",
                "audit.security",
                "security-terminal",
                "{\"eventId\":\"security-terminal\"}",
                "FAILED",
                5
        );
        jdbcTemplate.update("""
                UPDATE gateway.outbox_event
                SET last_attempt_at = TIMESTAMPTZ '2026-03-27T11:50:00Z',
                    last_error = 'Kafka unavailable'
                WHERE aggregate_id = 'security-terminal'
                """);

        gatewayOutboxPublisher.publishPending();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gateway.outbox_event WHERE aggregate_id = 'security-terminal'",
                String.class
        )).isEqualTo("FAILED");
        verify(kafkaTemplate, org.mockito.Mockito.never()).send("audit.security", "security-terminal", "{\"eventId\":\"security-terminal\"}");
    }

    private void insertOutboxRow(
            String id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String partitionKey,
            String payload,
            String status,
            int retryCount
    ) {
        jdbcTemplate.update("""
                INSERT INTO gateway.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, retry_count, created_at
                ) VALUES (
                    CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, CURRENT_TIMESTAMP
                )
                """,
                id,
                aggregateType,
                aggregateId,
                eventType,
                partitionKey,
                payload,
                status,
                retryCount
        );
    }

    private String gatewayToken(String username, Set<String> permissions, long policyVersion, long scopeVersion, String jti) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        return jwtService.encode(new FernJwtClaims(
                1L,
                username,
                Set.of("bootstrap_admin"),
                permissions,
                new ScopeRoots(java.util.List.of(1L), java.util.List.of()),
                policyVersion,
                scopeVersion,
                jti,
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
    }

    private String rawGatewayToken(String issuer, Set<String> audience, boolean omitIssuer, boolean omitAudience) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("gateway-gap-user")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("user_id", 1L)
                .claim("roles", List.of("bootstrap_admin"))
                .claim("permissions", List.of("org.region.read"))
                .claim("scope_roots", new LinkedHashMap<>(Map.of(
                        "system", false,
                        "regions", List.of(1L),
                        "outlets", List.of()
                )))
                .claim("policy_version", 1L)
                .claim("scope_version", 1L)
                .claim("auth_time", now.getEpochSecond())
                .claim("principal_type", "USER");
        if (!omitIssuer) {
            builder.issuer(issuer);
        }
        if (!omitAudience) {
            builder.audience(new ArrayList<>(audience));
        }
        return sign(builder.build());
    }

    private String sign(JWTClaimsSet claimsSet) {
        try {
            JWSSigner signer = new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claimsSet
            );
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign raw gateway token", exception);
        }
    }

    private void waitForRows(String aggregateType, int expectedRows) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM gateway.outbox_event WHERE aggregate_type = ?",
                    Integer.class,
                    aggregateType
            );
            if (count != null && count >= expectedRows) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for gateway outbox rows", exception);
            }
        }
        throw new AssertionError("Timed out waiting for gateway outbox rows for " + aggregateType);
    }
}
