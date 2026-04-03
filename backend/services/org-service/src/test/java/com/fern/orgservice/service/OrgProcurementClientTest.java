package com.fern.orgservice.service;

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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

class OrgProcurementClientTest {
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
    void shouldFetchOutletCloseCheckAndSendInternalHeaders() {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        server.createContext("/internal/procurement/outlet-close-check", exchange -> respond(exchange, 200, """
                {"outletId":101,"blockingPurchaseOrders":2,"blockingGoodsReceipts":1,"blockingSupplierInvoices":0,"hasBlockingDocuments":true}
                """, capturedHeaders));

        FernJwtService jwtService = jwtService();
        OrgProcurementClient client = new OrgProcurementClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("org-procurement-test"),
                clientSpec(),
                tokenSupport(jwtService),
                downstreamClientFactory(),
                errorMapper()
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-org-procurement");
        OrgProcurementClient.OutletCloseCheck response;
        try {
            response = client.getOutletCloseCheck(101L, actorPrincipal());
        } finally {
            MDC.clear();
        }

        assertThat(response).isEqualTo(new OrgProcurementClient.OutletCloseCheck(101L, 2L, 1L, 0L, true));
        FernJwtClaims claims = bearerClaims(jwtService, capturedHeaders.get());
        assertThat(claims.issuer()).isEqualTo("org-service");
        assertThat(claims.audience()).containsExactly("procurement-service");
        assertThat(claims.permissions()).contains(PermissionCodes.PROCUREMENT_INTERNAL_READ);
        assertThat(capturedHeaders.get().getFirst(CorrelationId.HEADER)).isEqualTo("corr-org-procurement");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USER_ID)).isEqualTo("71");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USERNAME)).isEqualTo("regional-manager");
    }

    @Test
    void shouldMapProcurementFailuresToDownstreamUnavailable() {
        server.createContext("/internal/procurement/outlet-close-check", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        OrgProcurementClient client = new OrgProcurementClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("org-procurement-test"),
                clientSpec(),
                tokenSupport(jwtService()),
                downstreamClientFactory(),
                errorMapper()
        );

        MDC.put(CorrelationId.MDC_KEY, "corr-org-procurement");
        try {
            assertThatThrownBy(() -> client.getOutletCloseCheck(101L, actorPrincipal()))
                    .isInstanceOf(DownstreamUnavailableException.class)
                    .hasMessage("procurement-service is unavailable");
        } finally {
            MDC.clear();
        }
    }

    private FernPrincipal actorPrincipal() {
        return new FernPrincipal(
                71L,
                "regional-manager",
                Set.of("REGION_MANAGER"),
                Set.of(),
                ScopeRoots.empty(),
                1L,
                1L,
                "org-procurement-test-jti"
        );
    }

    private FernDownstreamClientSpec clientSpec() {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return new FernDownstreamClientSpec("org-service", "procurement-service", "procurement", properties);
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
        properties.setSecret("org-procurement-client-test-secret-0123456789012345");
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
