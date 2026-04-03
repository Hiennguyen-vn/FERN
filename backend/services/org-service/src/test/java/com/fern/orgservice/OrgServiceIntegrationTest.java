package com.fern.orgservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.common.PermissionCodes;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.orgservice.service.OrgOutboxPublisher;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import org.apache.kafka.clients.producer.ProducerRecord;
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
    private static HttpServer posServer;
    private static HttpServer inventoryServer;
    private static HttpServer procurementServer;
    private static HttpServer financeServer;
    private static volatile boolean posHasOpenSessions;
    private static volatile boolean inventoryHasBlockingOperations;
    private static volatile long inventoryBlockingReservations;
    private static volatile long inventoryBlockingStockCountSessions;
    private static volatile boolean procurementHasBlockingDocuments;
    private static volatile long procurementBlockingPurchaseOrders;
    private static volatile long procurementBlockingGoodsReceipts;
    private static volatile long procurementBlockingSupplierInvoices;
    private static volatile boolean financeHasBlockingObligations;
    private static volatile long financeBlockingPayrollRuns;
    private static volatile String lastPosAuthorization;
    private static volatile String lastPosCorrelationId;
    private static volatile String lastPosActorUserId;
    private static volatile String lastPosActorUsername;
    private static volatile String lastPosQuery;
    private static volatile String lastInventoryAuthorization;
    private static volatile String lastInventoryCorrelationId;
    private static volatile String lastInventoryActorUserId;
    private static volatile String lastInventoryActorUsername;
    private static volatile String lastInventoryQuery;
    private static volatile String lastProcurementAuthorization;
    private static volatile String lastProcurementCorrelationId;
    private static volatile String lastProcurementActorUserId;
    private static volatile String lastProcurementActorUsername;
    private static volatile String lastProcurementQuery;
    private static volatile String lastFinanceAuthorization;
    private static volatile String lastFinanceCorrelationId;
    private static volatile String lastFinanceActorUserId;
    private static volatile String lastFinanceActorUsername;
    private static volatile String lastFinanceQuery;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.fern.orgservice.service.ScopeVersionService scopeVersionService;

    private String token;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ensurePosServerStarted();
        ensureInventoryServerStarted();
        ensureProcurementServerStarted();
        ensureFinanceServerStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("org"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> false);
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> true);
        registry.add("fern.clients.pos.base-url", () -> "http://localhost:" + posServer.getAddress().getPort());
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost:" + inventoryServer.getAddress().getPort());
        registry.add("fern.clients.procurement.base-url", () -> "http://localhost:" + procurementServer.getAddress().getPort());
        registry.add("fern.clients.finance.base-url", () -> "http://localhost:" + financeServer.getAddress().getPort());
    }

    @org.junit.jupiter.api.BeforeAll
    static void startPosServer() {
        ensurePosServerStarted();
        ensureInventoryServerStarted();
        ensureProcurementServerStarted();
        ensureFinanceServerStarted();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopPosServer() {
        if (posServer != null) {
            posServer.stop(0);
        }
        if (procurementServer != null) {
            procurementServer.stop(0);
        }
        if (inventoryServer != null) {
            inventoryServer.stop(0);
        }
        if (financeServer != null) {
            financeServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM org.outbox_event");
        redisTemplate.delete("fern:versions:scope");
        token = issueToken(1L);
        posHasOpenSessions = false;
        inventoryHasBlockingOperations = false;
        inventoryBlockingReservations = 0;
        inventoryBlockingStockCountSessions = 0;
        procurementHasBlockingDocuments = false;
        procurementBlockingPurchaseOrders = 0;
        procurementBlockingGoodsReceipts = 0;
        procurementBlockingSupplierInvoices = 0;
        financeHasBlockingObligations = false;
        financeBlockingPayrollRuns = 0;
        lastPosAuthorization = null;
        lastPosCorrelationId = null;
        lastPosActorUserId = null;
        lastPosActorUsername = null;
        lastPosQuery = null;
        lastInventoryAuthorization = null;
        lastInventoryCorrelationId = null;
        lastInventoryActorUserId = null;
        lastInventoryActorUsername = null;
        lastInventoryQuery = null;
        lastProcurementAuthorization = null;
        lastProcurementCorrelationId = null;
        lastProcurementActorUserId = null;
        lastProcurementActorUsername = null;
        lastProcurementQuery = null;
        lastFinanceAuthorization = null;
        lastFinanceCorrelationId = null;
        lastFinanceActorUserId = null;
        lastFinanceActorUsername = null;
        lastFinanceQuery = null;
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
    void shouldBrowseRegionsAndOutletsWithinAccessibleScope() throws Exception {
        mockMvc.perform(post("/regions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "REGION-BROWSE-1",
                                  "parentRegionId": 1,
                                  "currencyCode": "VND",
                                  "name": "Browse Region",
                                  "timezoneName": "Asia/Ho_Chi_Minh"
                                }
                                """))
                .andExpect(status().isOk());
        token = issueToken(currentScopeVersion());
        mockMvc.perform(post("/regions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "REGION-BROWSE-2",
                                  "parentRegionId": 1,
                                  "currencyCode": "VND",
                                  "name": "Browse Region Two",
                                  "timezoneName": "Asia/Ho_Chi_Minh"
                                }
                                """))
                .andExpect(status().isOk());

        Long regionId = jdbcTemplate.queryForObject("SELECT id FROM org.region WHERE code = 'REGION-BROWSE-1'", Long.class);
        token = issueToken(currentScopeVersion());

        mockMvc.perform(post("/outlets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regionId": %d,
                                  "code": "OUTLET-BROWSE-1",
                                  "name": "Browse Outlet",
                                  "status": "ACTIVE"
                                }
                                """.formatted(regionId)))
                .andExpect(status().isOk());
        token = issueToken(currentScopeVersion());
        mockMvc.perform(post("/outlets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regionId": %d,
                                  "code": "OUTLET-BROWSE-2",
                                  "name": "Browse Outlet Two",
                                  "status": "ACTIVE"
                                }
                                """.formatted(regionId)))
                .andExpect(status().isOk());

        token = issueToken(currentScopeVersion());

        mockMvc.perform(get("/regions")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "Browse")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("REGION-BROWSE-1"))
                .andExpect(jsonPath("$.items[0].name").value("Browse Region"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/outlets")
                        .header("Authorization", "Bearer " + token)
                        .param("regionId", Long.toString(regionId))
                        .param("status", "ACTIVE")
                        .param("search", "Browse")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("OUTLET-BROWSE-1"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void shouldRejectInternalScopeExpansionWhenServiceTokenAudienceIsWrong() throws Exception {
        String wrongAudienceToken = issueServiceToken(currentScopeVersion(), "inventory-service", Set.of("inventory-service"));

        mockMvc.perform(post("/internal/scopes/expand")
                        .header("Authorization", "Bearer " + wrongAudienceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[1],"outletIds":[]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectClosingOutletWhenOpenPosSessionExists() throws Exception {
        Long regionId = createRegion("REGION-CLOSE-GUARD-1", "Close Guard Region One");
        token = issueToken(currentScopeVersion());
        Long outletId = createOutlet(regionId, "OUTLET-CLOSE-GUARD-1", "Close Guard Outlet One");
        token = issueToken(currentScopeVersion());
        posHasOpenSessions = true;

        FernJwtService jwtService = testJwtService();
        FernJwtClaims actorClaims = jwtService.decode(token);

        mockMvc.perform(patch("/outlets/{id}", outletId)
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationId.HEADER, "corr-org-close-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CLOSED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot close outlet while open POS sessions still exist"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org.outlet WHERE id = ?", String.class, outletId))
                .isEqualTo("ACTIVE");
        assertThat(lastPosQuery)
                .contains("outletId=" + outletId)
                .contains("status=OPEN")
                .contains("limit=1");
        assertThat(lastPosCorrelationId).isEqualTo("corr-org-close-1");
        assertThat(lastPosActorUserId).isEqualTo(actorClaims.userId().toString());
        assertThat(lastPosActorUsername).isEqualTo(actorClaims.username());
        assertThat(lastPosAuthorization).isNotBlank().isNotEqualTo("Bearer " + token);

        FernJwtClaims serviceClaims = jwtService.decode(lastPosAuthorization.substring("Bearer ".length()));
        assertThat(serviceClaims.principalType()).isEqualTo(FernPrincipalType.SERVICE);
        assertThat(serviceClaims.audience()).contains("pos-service");
        assertThat(serviceClaims.permissions()).contains(PermissionCodes.POS_SESSION_READ);
    }

    @Test
    void shouldAllowClosingOutletWhenNoOpenPosSessionExists() throws Exception {
        Long regionId = createRegion("REGION-CLOSE-GUARD-2", "Close Guard Region Two");
        token = issueToken(currentScopeVersion());
        Long outletId = createOutlet(regionId, "OUTLET-CLOSE-GUARD-2", "Close Guard Outlet Two");
        token = issueToken(currentScopeVersion());

        mockMvc.perform(patch("/outlets/{id}", outletId)
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationId.HEADER, "corr-org-close-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CLOSED",
                                  "closedAt": "2026-04-03"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org.outlet WHERE id = ?", String.class, outletId))
                .isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("SELECT closed_at FROM org.outlet WHERE id = ?", java.time.LocalDate.class, outletId))
                .isEqualTo(java.time.LocalDate.parse("2026-04-03"));
        assertThat(lastPosQuery)
                .contains("outletId=" + outletId)
                .contains("status=OPEN")
                .contains("limit=1");
        assertThat(lastPosCorrelationId).isEqualTo("corr-org-close-2");
        assertThat(lastProcurementQuery).contains("outletId=" + outletId);
        assertThat(lastProcurementCorrelationId).isEqualTo("corr-org-close-2");
        assertThat(lastFinanceQuery).contains("outletId=" + outletId);
        assertThat(lastFinanceCorrelationId).isEqualTo("corr-org-close-2");
    }

    @Test
    void shouldRejectClosingOutletWhenProcurementDocumentsRemainOpen() throws Exception {
        Long regionId = createRegion("REGION-CLOSE-GUARD-3", "Close Guard Region Three");
        token = issueToken(currentScopeVersion());
        Long outletId = createOutlet(regionId, "OUTLET-CLOSE-GUARD-3", "Close Guard Outlet Three");
        token = issueToken(currentScopeVersion());
        procurementHasBlockingDocuments = true;
        procurementBlockingPurchaseOrders = 2;
        procurementBlockingGoodsReceipts = 1;
        procurementBlockingSupplierInvoices = 3;

        FernJwtService jwtService = testJwtService();
        FernJwtClaims actorClaims = jwtService.decode(token);

        mockMvc.perform(patch("/outlets/{id}", outletId)
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationId.HEADER, "corr-org-close-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CLOSED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot close outlet while procurement documents remain open: purchaseOrders=2, goodsReceipts=1, supplierInvoices=3"
                ));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org.outlet WHERE id = ?", String.class, outletId))
                .isEqualTo("ACTIVE");
        assertThat(lastProcurementQuery).contains("outletId=" + outletId);
        assertThat(lastProcurementCorrelationId).isEqualTo("corr-org-close-3");
        assertThat(lastProcurementActorUserId).isEqualTo(actorClaims.userId().toString());
        assertThat(lastProcurementActorUsername).isEqualTo(actorClaims.username());
        assertThat(lastProcurementAuthorization).isNotBlank().isNotEqualTo("Bearer " + token);

        FernJwtClaims serviceClaims = jwtService.decode(lastProcurementAuthorization.substring("Bearer ".length()));
        assertThat(serviceClaims.principalType()).isEqualTo(FernPrincipalType.SERVICE);
        assertThat(serviceClaims.audience()).contains("procurement-service");
        assertThat(serviceClaims.permissions()).contains(PermissionCodes.PROCUREMENT_INTERNAL_READ);
    }

    @Test
    void shouldRejectClosingOutletWhenInventoryWorkflowsRemainOpen() throws Exception {
        Long regionId = createRegion("REGION-CLOSE-GUARD-INV", "Close Guard Region Inventory");
        token = issueToken(currentScopeVersion());
        Long outletId = createOutlet(regionId, "OUTLET-CLOSE-GUARD-INV", "Close Guard Outlet Inventory");
        token = issueToken(currentScopeVersion());
        inventoryHasBlockingOperations = true;
        inventoryBlockingReservations = 2;
        inventoryBlockingStockCountSessions = 1;

        FernJwtService jwtService = testJwtService();
        FernJwtClaims actorClaims = jwtService.decode(token);

        mockMvc.perform(patch("/outlets/{id}", outletId)
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationId.HEADER, "corr-org-close-inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CLOSED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot close outlet while inventory workflows remain open: reservations=2, stockCountSessions=1"
                ));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org.outlet WHERE id = ?", String.class, outletId))
                .isEqualTo("ACTIVE");
        assertThat(lastInventoryQuery).contains("outletId=" + outletId);
        assertThat(lastInventoryCorrelationId).isEqualTo("corr-org-close-inventory");
        assertThat(lastInventoryActorUserId).isEqualTo(actorClaims.userId().toString());
        assertThat(lastInventoryActorUsername).isEqualTo(actorClaims.username());
        assertThat(lastInventoryAuthorization).isNotBlank().isNotEqualTo("Bearer " + token);

        FernJwtClaims serviceClaims = jwtService.decode(lastInventoryAuthorization.substring("Bearer ".length()));
        assertThat(serviceClaims.principalType()).isEqualTo(FernPrincipalType.SERVICE);
        assertThat(serviceClaims.audience()).contains("inventory-service");
        assertThat(serviceClaims.permissions()).contains(PermissionCodes.INVENTORY_INTERNAL_READ);
    }

    @Test
    void shouldRejectClosingOutletWhenFinanceObligationsRemainOpen() throws Exception {
        Long regionId = createRegion("REGION-CLOSE-GUARD-4", "Close Guard Region Four");
        token = issueToken(currentScopeVersion());
        Long outletId = createOutlet(regionId, "OUTLET-CLOSE-GUARD-4", "Close Guard Outlet Four");
        token = issueToken(currentScopeVersion());
        financeHasBlockingObligations = true;
        financeBlockingPayrollRuns = 2;

        FernJwtService jwtService = testJwtService();
        FernJwtClaims actorClaims = jwtService.decode(token);

        mockMvc.perform(patch("/outlets/{id}", outletId)
                        .header("Authorization", "Bearer " + token)
                        .header(CorrelationId.HEADER, "corr-org-close-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CLOSED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot close outlet while finance obligations remain open: payrollRuns=2"
                ));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM org.outlet WHERE id = ?", String.class, outletId))
                .isEqualTo("ACTIVE");
        assertThat(lastFinanceQuery).contains("outletId=" + outletId);
        assertThat(lastFinanceCorrelationId).isEqualTo("corr-org-close-4");
        assertThat(lastFinanceActorUserId).isEqualTo(actorClaims.userId().toString());
        assertThat(lastFinanceActorUsername).isEqualTo(actorClaims.username());
        assertThat(lastFinanceAuthorization).isNotBlank().isNotEqualTo("Bearer " + token);

        FernJwtClaims serviceClaims = jwtService.decode(lastFinanceAuthorization.substring("Bearer ".length()));
        assertThat(serviceClaims.principalType()).isEqualTo(FernPrincipalType.SERVICE);
        assertThat(serviceClaims.audience()).contains("finance-service");
        assertThat(serviceClaims.permissions()).contains(PermissionCodes.FINANCE_INTERNAL_READ);
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
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            sendStarted.countDown();
            return firstSend;
        });

        OrgOutboxPublisher firstPublisher = new OrgOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );
        OrgOutboxPublisher secondPublisher = new OrgOutboxPublisher(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                kafkaTemplate,
                Clock.fixed(Instant.parse("2026-03-27T12:00:00Z"), java.time.ZoneOffset.UTC),
                3,
                Duration.ofMinutes(1),
                new NoopOperationalAlertPublisher(),
                new SimpleMeterRegistry()
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> firstRun = executor.submit(firstPublisher::publishPending);
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            secondPublisher.publishPending();
            verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));

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

    @Test
    void shouldBumpScopeVersionAtomicallyUnderConcurrency() throws Exception {
        int taskCount = 8;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        long initial = currentScopeVersion();
        try {
            List<Future<Long>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return scopeVersionService.bump();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Long> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(currentScopeVersion()).isEqualTo(initial + taskCount);
        assertThat(redisTemplate.opsForValue().get("fern:versions:scope")).isEqualTo(Long.toString(initial + taskCount));
    }

    private String issueToken(long scopeVersion) {
        return issueToken(scopeVersion, com.fern.platform.common.FernPrincipalType.USER, false);
    }

    private String issueServiceToken(long scopeVersion) {
        return issueServiceToken(scopeVersion, "inventory-service", Set.of("org-service"));
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
                principalType,
                principalType == com.fern.platform.common.FernPrincipalType.SERVICE
                        ? "org-internal"
                        : FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                principalType == com.fern.platform.common.FernPrincipalType.SERVICE
                        ? Set.of("org-service")
                        : Set.of("org-service")
        ), jwtService.accessTokenTtl());
    }

    private String issueServiceToken(long scopeVersion, String callerService, Set<String> audience) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        Instant now = Instant.now();
        return jwtService.encode(new FernJwtClaims(
                null,
                callerService,
                Set.of(),
                Set.of("org.region.read", "org.region.write", "org.outlet.read", "org.outlet.write", "org.scope.resolve"),
                new com.fern.platform.common.ScopeRoots(true, List.of(), List.of()),
                1L,
                scopeVersion,
                "org-test-jti-service-" + callerService + "-" + scopeVersion,
                now,
                now.plus(jwtService.serviceTokenTtl()),
                com.fern.platform.common.FernPrincipalType.SERVICE,
                callerService,
                audience
        ), jwtService.serviceTokenTtl());
    }

    private long currentScopeVersion() {
        return jdbcTemplate.queryForObject("SELECT version FROM org.scope_version_state WHERE id = 1", Long.class);
    }

    private Long createRegion(String code, String name) throws Exception {
        mockMvc.perform(post("/regions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "parentRegionId": 1,
                                  "currencyCode": "VND",
                                  "name": "%s",
                                  "timezoneName": "Asia/Ho_Chi_Minh"
                                }
                                """.formatted(code, name)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM org.region WHERE code = ?", Long.class, code);
    }

    private Long createOutlet(Long regionId, String code, String name) throws Exception {
        mockMvc.perform(post("/outlets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regionId": %d,
                                  "code": "%s",
                                  "name": "%s",
                                  "status": "ACTIVE"
                                }
                                """.formatted(regionId, code, name)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("SELECT id FROM org.outlet WHERE code = ?", Long.class, code);
    }

    private FernJwtService testJwtService() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        return new FernJwtService(properties, Clock.systemUTC());
    }

    private static synchronized void ensurePosServerStarted() {
        if (posServer != null) {
            return;
        }
        try {
            posServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start POS stub server", exception);
        }
        posServer.createContext("/pos-sessions", exchange -> {
            lastPosAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastPosCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastPosActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastPosActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            lastPosQuery = exchange.getRequestURI().getRawQuery();
            byte[] body = (posHasOpenSessions ? "[{\"id\":7001,\"status\":\"OPEN\"}]" : "[]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        posServer.start();
    }

    private static synchronized void ensureProcurementServerStarted() {
        if (procurementServer != null) {
            return;
        }
        try {
            procurementServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start procurement stub server", exception);
        }
        procurementServer.createContext("/internal/procurement/outlet-close-check", exchange -> {
            lastProcurementAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastProcurementCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastProcurementActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastProcurementActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            lastProcurementQuery = exchange.getRequestURI().getRawQuery();
            String body = """
                    {
                      "outletId": 0,
                      "blockingPurchaseOrders": %d,
                      "blockingGoodsReceipts": %d,
                      "blockingSupplierInvoices": %d,
                      "hasBlockingDocuments": %s
                    }
                    """.formatted(
                    procurementBlockingPurchaseOrders,
                    procurementBlockingGoodsReceipts,
                    procurementBlockingSupplierInvoices,
                    procurementHasBlockingDocuments
            );
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        procurementServer.start();
    }

    private static synchronized void ensureInventoryServerStarted() {
        if (inventoryServer != null) {
            return;
        }
        try {
            inventoryServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start inventory stub server", exception);
        }
        inventoryServer.createContext("/internal/inventory/outlet-close-check", exchange -> {
            lastInventoryAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastInventoryCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastInventoryActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastInventoryActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            lastInventoryQuery = exchange.getRequestURI().getRawQuery();
            String body = """
                    {
                      "outletId": 0,
                      "blockingReservations": %d,
                      "blockingStockCountSessions": %d,
                      "hasBlockingOperations": %s
                    }
                    """.formatted(
                    inventoryBlockingReservations,
                    inventoryBlockingStockCountSessions,
                    inventoryHasBlockingOperations
            );
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        inventoryServer.start();
    }

    private static synchronized void ensureFinanceServerStarted() {
        if (financeServer != null) {
            return;
        }
        try {
            financeServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start finance stub server", exception);
        }
        financeServer.createContext("/internal/finance/outlet-close-check", exchange -> {
            lastFinanceAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastFinanceCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastFinanceActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastFinanceActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            lastFinanceQuery = exchange.getRequestURI().getRawQuery();
            String body = """
                    {
                      "outletId": 0,
                      "blockingPayrollRuns": %d,
                      "hasBlockingObligations": %s
                    }
                    """.formatted(financeBlockingPayrollRuns, financeHasBlockingObligations);
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        financeServer.start();
    }
}
