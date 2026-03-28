package com.fern.orgservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.orgservice.service.OrgOutboxPublisher;
import java.time.Duration;
import java.time.Clock;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrgServiceIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("org"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> false);
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> true);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM org.outbox_event");
        redisTemplate.delete("fern:versions:scope");
        token = issueToken(1L);
    }

    @Test
    void shouldCreateRegionOutletAndExpandScope() throws Exception {
        mockMvc.perform(post("/regions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "REGION-1",
                                  "parentRegionId": 1,
                                  "currencyCode": "VND",
                                  "name": "Region One",
                                  "timezoneName": "Asia/Ho_Chi_Minh"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGION-1"));

        Long regionId = jdbcTemplate.queryForObject("SELECT id FROM org.region WHERE code = 'REGION-1'", Long.class);
        token = issueToken(currentScopeVersion());

        mockMvc.perform(post("/outlets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regionId": %d,
                                  "code": "OUTLET-1",
                                  "name": "Outlet One",
                                  "status": "ACTIVE"
                                }
                                """.formatted(regionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OUTLET-1"));

        String serviceToken = issueServiceToken(currentScopeVersion());

        mockMvc.perform(post("/internal/scopes/expand")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[1],"outletIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionIds[0]").value(1))
                .andExpect(jsonPath("$.outletIds[0]").isNumber());

        String version = redisTemplate.opsForValue().get("fern:versions:scope");
        assertThat(version).isNotBlank();
    }

    @Test
    void shouldNotClaimSameOutboxEventAcrossConcurrentPublishers() throws Exception {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO org.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(? AS uuid), 'region', '10', 'org.region.changed', '10', ?, 'PENDING', CURRENT_TIMESTAMP
                )
                """, eventId, "{\"id\":10}");

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<Object> firstSend = new CompletableFuture<>();
        CountDownLatch sendStarted = new CountDownLatch(1);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            sendStarted.countDown();
            return firstSend;
        });

        OrgOutboxPublisher firstPublisher = new OrgOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1)
        );
        OrgOutboxPublisher secondPublisher = new OrgOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1)
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
                FROM org.outbox_event
                WHERE id = CAST(? AS uuid)
                """, String.class, eventId);
        assertThat(status).isEqualTo("PUBLISHED");
    }

    private String issueToken(long scopeVersion) {
        return issueToken(scopeVersion, com.fern.platform.common.FernPrincipalType.USER, false);
    }

    private String issueServiceToken(long scopeVersion) {
        return issueToken(scopeVersion, com.fern.platform.common.FernPrincipalType.SERVICE, true);
    }

    private String issueToken(
            long scopeVersion,
            com.fern.platform.common.FernPrincipalType principalType,
            boolean systemScoped
    ) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        return jwtService.encode(new FernJwtClaims(
                1L,
                principalType == com.fern.platform.common.FernPrincipalType.SERVICE ? "org-internal" : "bootstrap-admin",
                principalType == com.fern.platform.common.FernPrincipalType.SERVICE ? Set.of("org-service") : Set.of("bootstrap_admin"),
                Set.of("org.region.read", "org.region.write", "org.outlet.read", "org.outlet.write", "org.scope.resolve"),
                new com.fern.platform.common.ScopeRoots(systemScoped, List.of(1L), List.of()),
                1L,
                scopeVersion,
                "org-test-jti-" + principalType.name().toLowerCase() + "-" + scopeVersion,
                Instant.now(),
                Instant.now().plusSeconds(900),
                principalType
        ), jwtService.accessTokenTtl());
    }

    private long currentScopeVersion() {
        return jdbcTemplate.queryForObject("SELECT version FROM org.scope_version_state WHERE id = 1", Long.class);
    }
}
