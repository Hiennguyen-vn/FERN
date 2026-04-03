package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.platform.common.DownstreamUnavailableException;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientProperties;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.web.FernDownstreamErrorMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

class PayrollHrClientTest {
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
    void shouldFetchEffectiveContractsAndSendInternalHeaders() {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        server.createContext("/internal/hr/effective-contracts", exchange -> respond(exchange, 200, """
                [{"contractId":1,"employeeId":2,"regionId":3,"employmentType":"FULL_TIME","salaryType":"MONTHLY","baseSalary":1500.0,"taxCode":"TAX-1","startDate":"2026-03-01","endDate":null}]
                """, capturedHeaders));

        FernJwtService jwtService = jwtService();
        PayrollHrClient client = new PayrollHrClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("hr-test"),
                tokenSupport(jwtService),
                hrClientSpec(),
                downstreamClientFactory(),
                errorMapper()
        );

        List<EffectiveContract> response = client.fetchEffectiveContracts(3L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "corr-123", 99L);

        assertThat(response).containsExactly(new EffectiveContract(
                1L, 2L, 3L, "FULL_TIME", "MONTHLY", response.getFirst().baseSalary(), "TAX-1", LocalDate.of(2026, 3, 1), null
        ));
        String bearer = capturedHeaders.get().getFirst("Authorization");
        assertThat(bearer).startsWith("Bearer ");
        FernJwtClaims claims = jwtService.decode(bearer.substring("Bearer ".length()));
        assertThat(claims.issuer()).isEqualTo("finance-service");
        assertThat(claims.audience()).containsExactly("hr-service");
        assertThat(capturedHeaders.get().getFirst(CorrelationId.HEADER)).isEqualTo("corr-123");
        assertThat(capturedHeaders.get().getFirst(FernRequestHeaders.ACTOR_USER_ID)).isEqualTo("99");
    }

    @Test
    void shouldMapApprovedAttendanceFailureToBadRequest() {
        server.createContext("/internal/hr/approved-attendance", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        PayrollHrClient client = new PayrollHrClient(
                RestClient.builder().baseUrl(baseUrl).build(),
                CircuitBreaker.ofDefaults("hr-test"),
                tokenSupport(jwtService()),
                hrClientSpec(),
                downstreamClientFactory(),
                errorMapper()
        );

        assertThatThrownBy(() -> client.fetchApprovedAttendance(3L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "corr-456", 88L))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessage("hr-service is unavailable");
    }

    private FernDownstreamClientSpec hrClientSpec() {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl(baseUrl);
        return new FernDownstreamClientSpec("finance-service", "hr-service", "hr", properties);
    }

    private FernDownstreamClientFactory downstreamClientFactory() {
        return new FernDownstreamClientFactory(new SimpleMeterRegistry());
    }

    private FernDownstreamErrorMapper errorMapper() {
        return new FernDownstreamErrorMapper(new ObjectMapper().findAndRegisterModules());
    }

    private FernJwtService jwtService() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("finance-hr-client-test-secret-012345678901234567890");
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
