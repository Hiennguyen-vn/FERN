package com.fern.reportservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.platform.contracts.SupplierInvoiceApprovedEvent;
import com.fern.platform.contracts.SupplierInvoiceApprovedLine;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.reportservice.service.ReportService;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReportServiceIntegrationTest {
    private static final Path EXPORT_DIR = createExportDir();
    private static final AtomicLong TOKEN_SEQUENCE = new AtomicLong();
    private static HttpServer posServer;
    private static HttpServer inventoryServer;
    private static volatile String posStatsTodayResponseBody = "[]";
    private static volatile String lastPosAuthorization;
    private static volatile String lastPosCorrelationId;
    private static volatile String lastPosActorUserId;
    private static volatile String lastPosActorUsername;
    private static volatile String inventoryStockBalancesResponseBody = """
            {"items":[{"ingredientId":501,"qtyOnHand":12.5,"qtyReserved":1.5,"qtyAvailable":11.0}],"page":0,"size":50,"hasMore":false}
            """;
    private static volatile String inventoryTransactionsResponseBody = """
            {"items":[{"factId":1,"movementType":"PURCHASE_IN"}],"page":0,"size":50,"hasMore":false}
            """;
    private static volatile String lastInventoryAuthorization;
    private static volatile String lastInventoryCorrelationId;
    private static volatile String lastInventoryActorUserId;
    private static volatile String lastInventoryActorUsername;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensurePosServerStarted();
        ensureInventoryServerStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("report"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.report.export.base-dir", () -> EXPORT_DIR.toString());
        registry.add("fern.report.export.preview-row-limit", () -> "5");
        registry.add("fern.report.export.worker-delay-ms", () -> "60000");
        registry.add("fern.clients.pos.base-url", () -> "http://localhost:" + posServer.getAddress().getPort());
        registry.add("fern.clients.inventory.base-url", () -> "http://localhost:" + inventoryServer.getAddress().getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeAll
    static void startPosServer() throws IOException {
        posServer = HttpServer.create(new InetSocketAddress(0), 0);
        posServer.createContext("/pos-stats/today", exchange -> {
            lastPosAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastPosCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastPosActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastPosActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            byte[] body = posStatsTodayResponseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        posServer.start();

        inventoryServer = HttpServer.create(new InetSocketAddress(0), 0);
        inventoryServer.createContext("/stock-balances", exchange -> {
            lastInventoryAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastInventoryCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastInventoryActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastInventoryActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            byte[] body = inventoryStockBalancesResponseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        inventoryServer.createContext("/inventory-transactions", exchange -> {
            lastInventoryAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastInventoryCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationId.HEADER);
            lastInventoryActorUserId = exchange.getRequestHeaders().getFirst("X-Fern-Actor-User-Id");
            lastInventoryActorUsername = exchange.getRequestHeaders().getFirst("X-Fern-Actor-Username");
            byte[] body = inventoryTransactionsResponseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        inventoryServer.start();
    }

    @AfterAll
    static void stopPosServer() {
        if (posServer != null) {
            posServer.stop(0);
        }
        if (inventoryServer != null) {
            inventoryServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    report.outbox_event,
                    report.company_daily_outlet,
                    report.region_daily_event,
                    report.export_job,
                    report.projection_watermark,
                    report.inventory_stock_snapshot,
                    report.payables_fact,
                    report.company_daily_summary,
                    report.region_daily_summary,
                    report.expense_fact,
                    report.payroll_fact,
                    report.attendance_fact,
                    report.procurement_fact,
                    report.inventory_movement_fact,
                    report.payment_fact,
                    report.sales_fact,
                    raw_events.event_landing
                RESTART IDENTITY CASCADE
                """);
        try (var stream = Files.list(EXPORT_DIR)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to clean export artifact " + path, exception);
                }
            });
        }
        posStatsTodayResponseBody = "[]";
        lastPosAuthorization = null;
        lastPosCorrelationId = null;
        lastPosActorUserId = null;
        lastPosActorUsername = null;
        inventoryStockBalancesResponseBody = """
                {"items":[{"ingredientId":501,"qtyOnHand":12.5,"qtyReserved":1.5,"qtyAvailable":11.0}],"page":0,"size":50,"hasMore":false}
                """;
        inventoryTransactionsResponseBody = """
                {"items":[{"factId":1,"movementType":"PURCHASE_IN"}],"page":0,"size":50,"hasMore":false}
                """;
        lastInventoryAuthorization = null;
        lastInventoryCorrelationId = null;
        lastInventoryActorUserId = null;
        lastInventoryActorUsername = null;
    }

    @Test
    void shouldReturnOutletRevenueTodayStatsThroughPosFacade() throws Exception {
        posStatsTodayResponseBody = """
                [
                  {
                    "outletId": 101,
                    "sessionId": 9001,
                    "sessionStatus": "OPEN",
                    "currencyCode": "VND",
                    "totalOrders": 4,
                    "completed": 3,
                    "open": 1,
                    "cancelled": 0,
                    "totalRevenue": 125.50,
                    "cashCollected": 40.00,
                    "nonCashCollected": 85.50
                  }
                ]
                """;
        String authorizationHeader = bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), List.of(101L), false);
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        FernJwtClaims claims = jwtService.decode(authorizationHeader.substring("Bearer ".length()));

        mockMvc.perform(get("/reports/revenue/outlet-stats/today")
                        .header("Authorization", authorizationHeader)
                        .header(CorrelationId.HEADER, "corr-report-revenue-1")
                        .param("outletIds", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outletId").value(101))
                .andExpect(jsonPath("$[0].sessionId").value(9001))
                .andExpect(jsonPath("$[0].sessionStatus").value("OPEN"))
                .andExpect(jsonPath("$[0].currencyCode").value("VND"))
                .andExpect(jsonPath("$[0].totalOrders").value(4))
                .andExpect(jsonPath("$[0].completed").value(3))
                .andExpect(jsonPath("$[0].open").value(1))
                .andExpect(jsonPath("$[0].cancelled").value(0))
                .andExpect(jsonPath("$[0].totalRevenue").value(125.5))
                .andExpect(jsonPath("$[0].cashCollected").value(40.0))
                .andExpect(jsonPath("$[0].nonCashCollected").value(85.5));

        assertThat(lastPosCorrelationId).isEqualTo("corr-report-revenue-1");
        assertThat(lastPosActorUserId).isEqualTo(claims.userId().toString());
        assertThat(lastPosActorUsername).isEqualTo(claims.username());
        assertThat(lastPosAuthorization).isNotBlank().isNotEqualTo(authorizationHeader);
        assertThat(jwtService.decode(lastPosAuthorization.substring("Bearer ".length())).principalType())
                .isEqualTo(FernPrincipalType.SERVICE);
    }

    @Test
    void shouldReturnNoSessionOutletRevenueRowThroughPosFacade() throws Exception {
        posStatsTodayResponseBody = """
                [
                  {
                    "outletId": 102,
                    "sessionId": null,
                    "sessionStatus": "NO_SESSION",
                    "currencyCode": "VND",
                    "totalOrders": 0,
                    "completed": 0,
                    "open": 0,
                    "cancelled": 0,
                    "totalRevenue": 0,
                    "cashCollected": 0,
                    "nonCashCollected": 0
                  }
                ]
                """;

        mockMvc.perform(get("/reports/revenue/outlet-stats/today")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), List.of(102L), false))
                        .param("outletIds", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outletId").value(102))
                .andExpect(jsonPath("$[0].sessionId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].sessionStatus").value("NO_SESSION"))
                .andExpect(jsonPath("$[0].currencyCode").value("VND"))
                .andExpect(jsonPath("$[0].totalOrders").value(0))
                .andExpect(jsonPath("$[0].completed").value(0))
                .andExpect(jsonPath("$[0].open").value(0))
                .andExpect(jsonPath("$[0].cancelled").value(0))
                .andExpect(jsonPath("$[0].totalRevenue").value(0))
                .andExpect(jsonPath("$[0].cashCollected").value(0))
                .andExpect(jsonPath("$[0].nonCashCollected").value(0));
    }

    @Test
    void shouldDenyRevenueStatsForPayrollOnlyPermission() throws Exception {
        mockMvc.perform(get("/reports/revenue/outlet-stats/today")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_PAYROLL_READ), List.of(1L), List.of(101L), false))
                        .param("outletIds", "101"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldServeDeprecatedInventoryProxyWithServiceTokenAndSunsetHeaders() throws Exception {
        String authorizationHeader = bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), List.of(101L), false);
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        FernJwtClaims claims = jwtService.decode(authorizationHeader.substring("Bearer ".length()));

        mockMvc.perform(get("/reports/inventory/stock-balances")
                        .header("Authorization", authorizationHeader)
                        .header(CorrelationId.HEADER, "corr-report-inventory-compat")
                        .param("outletId", "101"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Sunset", "Wed, 31 Dec 2026 23:59:59 GMT"))
                .andExpect(jsonPath("$.items[0].ingredientId").value(501))
                .andExpect(jsonPath("$.items[0].qtyReserved").value(1.5))
                .andExpect(jsonPath("$.items[0].qtyAvailable").value(11.0));

        assertThat(lastInventoryCorrelationId).isEqualTo("corr-report-inventory-compat");
        assertThat(lastInventoryActorUserId).isEqualTo(claims.userId().toString());
        assertThat(lastInventoryActorUsername).isEqualTo(claims.username());
        assertThat(lastInventoryAuthorization).isNotBlank().isNotEqualTo(authorizationHeader);
        assertThat(jwtService.decode(lastInventoryAuthorization.substring("Bearer ".length())).principalType())
                .isEqualTo(FernPrincipalType.SERVICE);
    }

    @Test
    void shouldReadInventoryStockBalanceSnapshotsFromProjection() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO report.inventory_stock_snapshot (
                    snapshot_id, region_id, outlet_id, ingredient_id, qty_on_hand, unit_cost, last_count_date, last_movement_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                91001L, 1L, 101L, 501L, new BigDecimal("12.50"), new BigDecimal("4.20"), LocalDate.parse("2026-03-27"), OffsetDateTime.parse("2026-03-27T08:00:00Z"),
                91002L, 1L, 101L, 502L, new BigDecimal("6.75"), new BigDecimal("3.10"), LocalDate.parse("2026-03-28"), OffsetDateTime.parse("2026-03-28T10:15:00Z")
        );

        mockMvc.perform(get("/reports/inventory/stock-balance-snapshots")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), List.of(101L), false))
                        .param("outletId", "101")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].regionId").value(1))
                .andExpect(jsonPath("$.items[0].outletId").value(101))
                .andExpect(jsonPath("$.items[0].ingredientId").value(501))
                .andExpect(jsonPath("$.items[0].qtyOnHand").value(12.5))
                .andExpect(jsonPath("$.items[0].unitCost").value(4.2))
                .andExpect(jsonPath("$.items[0].lastCountDate[0]").value(2026))
                .andExpect(jsonPath("$.items[0].lastCountDate[1]").value(3))
                .andExpect(jsonPath("$.items[0].lastCountDate[2]").value(27))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void shouldReadInventoryTransactionFactsFromProjection() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO report.inventory_movement_fact (
                    fact_id, source_event_id, source_service, event_type, occurred_at, idempotency_key,
                    region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost,
                    source_reference_type, source_reference_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                92001L, "evt-1", "inventory-service", "inventory.adjusted", OffsetDateTime.parse("2026-03-27T08:00:00Z"), "idem-1",
                1L, 101L, 501L, LocalDate.parse("2026-03-27"), "PURCHASE_IN", new BigDecimal("5.00"), new BigDecimal("2.50"),
                "GOODS_RECEIPT", "GR-1",
                92002L, "evt-2", "inventory-service", "inventory.adjusted", OffsetDateTime.parse("2026-03-27T09:30:00Z"), "idem-2",
                1L, 101L, 501L, LocalDate.parse("2026-03-27"), "SALE_USAGE", new BigDecimal("-2.00"), new BigDecimal("2.50"),
                "POS_ORDER", "SO-1"
        );

        mockMvc.perform(get("/reports/inventory/transaction-facts")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), List.of(101L), false))
                        .param("outletId", "101")
                        .param("ingredientId", "501")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourceEventId").value("evt-2"))
                .andExpect(jsonPath("$.items[0].movementType").value("SALE_USAGE"))
                .andExpect(jsonPath("$.items[0].qtyChange").value(-2.0))
                .andExpect(jsonPath("$.items[0].sourceReferenceType").value("POS_ORDER"))
                .andExpect(jsonPath("$.items[1].sourceEventId").value("evt-1"))
                .andExpect(jsonPath("$.items[1].movementType").value("PURCHASE_IN"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void shouldProjectInventorySnapshotsOnlyFromInventoryOwnedEvents() throws Exception {
        ProcurementGoodsReceiptPostedEvent goodsReceiptEvent = new ProcurementGoodsReceiptPostedEvent(
                "report-gr-1",
                "procurement.goods_receipt.posted",
                Instant.parse("2026-03-27T08:00:00Z"),
                "procurement-service",
                "corr-report-gr-1",
                "idem-report-gr-1",
                91001L,
                81001L,
                1L,
                101L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T08:00:00Z"),
                3L,
                List.of(new GoodsReceiptPostedLine(501L, new BigDecimal("10.0000"), new BigDecimal("7.00"), 3001L))
        );
        reportService.ingestGoodsReceiptPosted(objectMapper.writeValueAsString(goodsReceiptEvent), goodsReceiptEvent);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.procurement_fact", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.inventory_movement_fact", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.inventory_stock_snapshot", Integer.class)).isZero();

        InventoryAdjustmentPostedEvent purchaseInEvent = new InventoryAdjustmentPostedEvent(
                "inventory-purchase-in-1",
                "inventory.adjustment.posted",
                Instant.parse("2026-03-27T08:05:00Z"),
                "inventory-service",
                "corr-inventory-purchase-in-1",
                "inventory-purchase-in-idem-1",
                null,
                1L,
                101L,
                501L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T08:05:00Z"),
                3L,
                "IN",
                "PURCHASE_IN",
                new BigDecimal("10.0000"),
                new BigDecimal("7.00"),
                "GOODS_RECEIPT_LINE",
                "3001"
        );
        reportService.ingestInventoryAdjustmentPosted(objectMapper.writeValueAsString(purchaseInEvent), purchaseInEvent);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.inventory_movement_fact", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM report.inventory_stock_snapshot
                WHERE outlet_id = 101 AND ingredient_id = 501
                """, BigDecimal.class)).isEqualByComparingTo("10.0000");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT inventory_value
                FROM report.inventory_stock_snapshot
                WHERE outlet_id = 101 AND ingredient_id = 501
                """, BigDecimal.class)).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldApplyWeightedAverageInventoryValuationAcrossInboundAndOutboundMovements() throws Exception {
        InventoryAdjustmentPostedEvent firstInbound = new InventoryAdjustmentPostedEvent(
                "inventory-weighted-1",
                "inventory.adjustment.posted",
                Instant.parse("2026-03-27T09:00:00Z"),
                "inventory-service",
                "corr-inventory-weighted-1",
                "inventory-weighted-idem-1",
                99011L,
                1L,
                101L,
                501L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T09:00:00Z"),
                7L,
                "IN",
                "PURCHASE_IN",
                new BigDecimal("10.0000"),
                new BigDecimal("7.00"),
                "GOODS_RECEIPT_LINE",
                "3001"
        );
        InventoryAdjustmentPostedEvent secondInbound = new InventoryAdjustmentPostedEvent(
                "inventory-weighted-2",
                "inventory.adjustment.posted",
                Instant.parse("2026-03-27T09:10:00Z"),
                "inventory-service",
                "corr-inventory-weighted-2",
                "inventory-weighted-idem-2",
                99012L,
                1L,
                101L,
                501L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T09:10:00Z"),
                7L,
                "IN",
                "PURCHASE_IN",
                new BigDecimal("5.0000"),
                new BigDecimal("10.00"),
                "GOODS_RECEIPT_LINE",
                "3002"
        );
        InventoryAdjustmentPostedEvent outbound = new InventoryAdjustmentPostedEvent(
                "inventory-weighted-3",
                "inventory.adjustment.posted",
                Instant.parse("2026-03-27T09:20:00Z"),
                "inventory-service",
                "corr-inventory-weighted-3",
                "inventory-weighted-idem-3",
                99013L,
                1L,
                101L,
                501L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T09:20:00Z"),
                7L,
                "OUT",
                "CORRECTION",
                new BigDecimal("-6.0000"),
                null,
                "STOCK_ADJUSTMENT",
                "99013"
        );

        reportService.ingestInventoryAdjustmentPosted(objectMapper.writeValueAsString(firstInbound), firstInbound);
        reportService.ingestInventoryAdjustmentPosted(objectMapper.writeValueAsString(secondInbound), secondInbound);
        reportService.ingestInventoryAdjustmentPosted(objectMapper.writeValueAsString(outbound), outbound);

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM report.inventory_stock_snapshot
                WHERE outlet_id = 101 AND ingredient_id = 501
                """, BigDecimal.class);
        BigDecimal inventoryValue = jdbcTemplate.queryForObject("""
                SELECT inventory_value
                FROM report.inventory_stock_snapshot
                WHERE outlet_id = 101 AND ingredient_id = 501
                """, BigDecimal.class);
        BigDecimal unitCost = jdbcTemplate.queryForObject("""
                SELECT unit_cost
                FROM report.inventory_stock_snapshot
                WHERE outlet_id = 101 AND ingredient_id = 501
                """, BigDecimal.class);

        assertThat(qtyOnHand).isEqualByComparingTo("9.0000");
        assertThat(inventoryValue).isEqualByComparingTo("72.00");
        assertThat(unitCost).isEqualByComparingTo("8.00");
    }

    @Test
    void shouldProjectSupplierInvoiceApprovalAndPaymentAllocationsIntoPayablesFact() throws Exception {
        SupplierInvoiceApprovedEvent invoiceApprovedEvent = new SupplierInvoiceApprovedEvent(
                "supplier-invoice-report-1",
                "procurement.supplier_invoice.approved",
                Instant.parse("2026-03-27T10:00:00Z"),
                "procurement-service",
                "corr-supplier-invoice-report-1",
                "supplier-invoice-report-idem-1",
                8801L,
                7001L,
                1L,
                101L,
                "VND",
                "INV-8801",
                LocalDate.parse("2026-03-27"),
                LocalDate.parse("2026-04-10"),
                new BigDecimal("36.25"),
                new BigDecimal("5.00"),
                new BigDecimal("41.25"),
                new BigDecimal("36.25"),
                new BigDecimal("5.00"),
                Instant.parse("2026-03-27T10:00:00Z"),
                9L,
                List.of(new SupplierInvoiceApprovedLine(
                        501L,
                        1,
                        "STOCK",
                        3001L,
                        "Milk delivery",
                        new BigDecimal("3.0000"),
                        new BigDecimal("12.0833"),
                        new BigDecimal("5.00"),
                        new BigDecimal("41.25")
                ))
        );
        SupplierPaymentRecordedEvent paymentRecordedEvent = new SupplierPaymentRecordedEvent(
                "supplier-payment-report-1",
                "procurement.supplier.payment.recorded",
                Instant.parse("2026-03-27T11:00:00Z"),
                "procurement-service",
                "corr-supplier-payment-report-1",
                "supplier-payment-report-idem-1",
                9901L,
                7001L,
                Instant.parse("2026-03-27T11:00:00Z"),
                new BigDecimal("41.25"),
                "VND",
                List.of(new SupplierPaymentAllocation(8801L, new BigDecimal("41.25"))),
                9L
        );

        reportService.ingestSupplierInvoiceApproved(objectMapper.writeValueAsString(invoiceApprovedEvent), invoiceApprovedEvent);
        reportService.ingestSupplierPaymentRecorded(objectMapper.writeValueAsString(paymentRecordedEvent), paymentRecordedEvent);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.payables_fact", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT variance_amount
                FROM report.payables_fact
                WHERE supplier_invoice_id = 8801
                  AND fact_type = 'SUPPLIER_INVOICE_APPROVED'
                """, BigDecimal.class)).isEqualByComparingTo("5.00");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT amount
                FROM report.payables_fact
                WHERE supplier_payment_id = 9901
                  AND fact_type = 'SUPPLIER_PAYMENT_ALLOCATION'
                """, BigDecimal.class)).isEqualByComparingTo("41.25");
    }

    @Test
    void shouldBackfillSaleOrderCogsWithoutDuplicatingAcrossAllSaleLines() throws Exception {
        PosSaleCompletedEvent saleEvent = new PosSaleCompletedEvent(
                "sale-event-cogs-1",
                "pos.sale.completed",
                Instant.parse("2026-03-27T08:00:00Z"),
                "pos-service",
                "corr-sale-event-cogs-1",
                "sale-idem-cogs-1",
                81001L,
                91001L,
                1L,
                101L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T08:05:00Z"),
                5L,
                null,
                List.of(new SalePaymentSnapshot(61L, "CASH", new BigDecimal("150.00"), "CAPTURED", Instant.parse("2026-03-27T08:05:00Z"), null)),
                Map.of("lines", List.of(
                        Map.of("productId", 501L, "qty", 1, "lineTotal", 100.00, "discountAmount", 0, "taxAmount", 0),
                        Map.of("productId", 502L, "qty", 1, "lineTotal", 50.00, "discountAmount", 0, "taxAmount", 0)
                )),
                List.of()
        );
        reportService.ingestPosSaleCompleted(objectMapper.writeValueAsString(saleEvent), saleEvent);

        InventoryAdjustmentPostedEvent saleUsageEvent = new InventoryAdjustmentPostedEvent(
                "inventory-sale-usage-1",
                "inventory.adjustment.posted",
                Instant.parse("2026-03-27T08:06:00Z"),
                "inventory-service",
                "corr-inventory-sale-usage-1",
                "inventory-idem-sale-usage-1",
                99001L,
                1L,
                101L,
                601L,
                LocalDate.parse("2026-03-27"),
                Instant.parse("2026-03-27T08:06:00Z"),
                7L,
                "OUT",
                "SALE_USAGE",
                new BigDecimal("-3.00"),
                new BigDecimal("10.00"),
                "SALE_ORDER",
                "81001"
        );
        reportService.ingestInventoryAdjustmentPosted(objectMapper.writeValueAsString(saleUsageEvent), saleUsageEvent);

        List<BigDecimal> cogsPerLine = jdbcTemplate.query(
                """
                        SELECT cogs_amount
                        FROM report.sales_fact
                        WHERE sale_order_id = 81001
                        ORDER BY line_number
                        """,
                (rs, rowNum) -> rs.getBigDecimal("cogs_amount")
        );
        BigDecimal totalCogs = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cogs_amount), 0) FROM report.sales_fact WHERE sale_order_id = 81001",
                BigDecimal.class
        );

        assertThat(cogsPerLine).containsExactly(new BigDecimal("20.00"), new BigDecimal("10.00"));
        assertThat(totalCogs).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldReportProjectionFreshnessFromWatermarkTable() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO report.projection_watermark (
                    dataset, last_occurred_at, last_ingested_at, failed_landing_count, updated_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                "inventory_stock_snapshot",
                OffsetDateTime.parse("2026-03-27T08:00:00Z"),
                OffsetDateTime.parse("2026-03-27T08:05:00Z"),
                2L
        );

        mockMvc.perform(get("/internal/report/projection-freshness")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), List.of(101L), false))
                        .param("dataset", "inventory_stock_snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataset").value("inventory_stock_snapshot"))
                .andExpect(jsonPath("$[0].lagMillis").value(300000))
                .andExpect(jsonPath("$[0].failedLandingCount").value(2));
    }

    @Test
    void shouldIngestExpenseEventsIdempotentlyAndRefreshNaturalKeySummaries() throws Exception {
        ExpensePostedEvent first = expenseEvent("expense-event-1", "expense-idem-1", 9001L, new BigDecimal("120.50"));
        ExpensePostedEvent second = expenseEvent("expense-event-2", "expense-idem-2", 9002L, new BigDecimal("79.50"));

        reportService.ingestExpensePosted(objectMapper.writeValueAsString(first), first);
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(first), first);
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(second), second);

        Integer expenseFactCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.expense_fact", Integer.class);
        Integer landingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_events.event_landing", Integer.class);
        Integer regionSummaryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.region_daily_summary", Integer.class);
        BigDecimal regionTotalExpense = jdbcTemplate.queryForObject(
                "SELECT total_expense FROM report.region_daily_summary WHERE region_id = 1 AND business_date = DATE '2026-03-27'",
                BigDecimal.class
        );
        BigDecimal companyTotalExpense = jdbcTemplate.queryForObject(
                "SELECT total_expense FROM report.company_daily_summary WHERE business_date = DATE '2026-03-27'",
                BigDecimal.class
        );
        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT transaction_count FROM report.region_daily_summary WHERE region_id = 1 AND business_date = DATE '2026-03-27'",
                Long.class
        );

        assertThat(expenseFactCount).isEqualTo(2);
        assertThat(landingCount).isEqualTo(2);
        assertThat(regionSummaryCount).isEqualTo(1);
        assertThat(regionTotalExpense).isEqualByComparingTo("200.00");
        assertThat(companyTotalExpense).isEqualByComparingTo("200.00");
        assertThat(transactionCount).isEqualTo(2L);
    }

    @Test
    void shouldTrackProjectionLagForDelayedExpenseEventWithoutDriftingSummary() throws Exception {
        Instant occurredAt = Instant.now().minusSeconds(7200);
        ExpensePostedEvent delayed = new ExpensePostedEvent(
                "expense-event-lag-1",
                "finance.expense.posted",
                occurredAt,
                "finance-service",
                "corr-expense-event-lag-1",
                "expense-idem-lag-1",
                9901L,
                1L,
                101L,
                501L,
                7001L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                new BigDecimal("42.25"),
                "PAYROLL_RUN",
                "7001"
        );

        reportService.ingestExpensePosted(objectMapper.writeValueAsString(delayed), delayed);
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(delayed), delayed);

        double lagMillis = meterRegistry.get("fern_projection_consumer_lag").gauge().value();
        Integer landingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM raw_events.event_landing
                WHERE source_event_id = 'expense-event-lag-1'
                """, Integer.class);
        Integer expenseFactCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report.expense_fact
                WHERE source_event_id = 'expense-event-lag-1'
                """, Integer.class);
        BigDecimal regionTotalExpense = jdbcTemplate.queryForObject("""
                SELECT total_expense
                FROM report.region_daily_summary
                WHERE region_id = 1 AND business_date = DATE '2026-03-27'
                """, BigDecimal.class);

        assertThat(lagMillis).isGreaterThanOrEqualTo(7_000_000d);
        assertThat(landingCount).isEqualTo(1);
        assertThat(expenseFactCount).isEqualTo(1);
        assertThat(regionTotalExpense).isEqualByComparingTo("42.25");
    }

    @Test
    void shouldCreateAndProcessExpenseExportAsyncIdempotently() throws Exception {
        ExpensePostedEvent expense = expenseEvent("expense-event-export", "expense-idem-export", 9100L, new BigDecimal("88.00"));
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(expense), expense);

        String response = mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-expense-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(response).get("exportJobId").asLong();

        String secondResponse = mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-expense-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(secondResponse).get("exportJobId").asLong()).isEqualTo(jobId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.export_job", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report.outbox_event
                WHERE event_type = 'audit.event'
                  AND payload::text LIKE '%report.export.requested%'
                """, Integer.class)).isEqualTo(1);

        reportService.processQueuedExports();
        reportService.processQueuedExports();

        mockMvc.perform(get("/reports/exports/{jobId}", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.preview[0].expense_record_id").value(9100));

        mockMvc.perform(get("/reports/exports/{jobId}/preview", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].expense_record_id").value(9100));

        String csv = mockMvc.perform(get("/reports/exports/{jobId}/download", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("expense_record_id");
        assertThat(csv).contains("9100");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM report.export_job WHERE export_job_id = ?", String.class, jobId))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM report.outbox_event
                WHERE event_type = 'audit.event'
                  AND payload::text LIKE '%report.export.completed%'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldListOnlyReadableExportJobsFromServerBackedHistory() throws Exception {
        ExpensePostedEvent expense = expenseEvent("expense-event-list", "expense-idem-list", 9300L, new BigDecimal("42.00"));
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(expense), expense);

        objectMapper.readTree(mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-list-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("exportJobId").asLong();
        Long latestJobId = objectMapper.readTree(mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-list-2")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("exportJobId").asLong();

        reportService.processQueuedExports();

        mockMvc.perform(get("/reports/exports")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_READ), List.of(1L), false))
                        .param("page", "0")
                        .param("size", "1")
                        .param("dataset", "EXPENSE_FACT")
                        .param("status", "COMPLETED")
                        .param("regionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].exportJobId").value(latestJobId))
                .andExpect(jsonPath("$.items[0].dataset").value("EXPENSE_FACT"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/reports/exports")
                        .header("Authorization", bearer(Set.of(PermissionCodes.REPORT_EXPORT), List.of(1L), false))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void shouldBoundPreviewWhileExportingMultipleRows() throws Exception {
        for (long index = 0; index < 8; index++) {
            ExpensePostedEvent expense = expenseEvent(
                    "expense-event-bulk-" + index,
                    "expense-idem-bulk-" + index,
                    9200L + index,
                    new BigDecimal("10.00").add(BigDecimal.valueOf(index))
            );
            reportService.ingestExpensePosted(objectMapper.writeValueAsString(expense), expense);
        }

        Long jobId = objectMapper.readTree(mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-expense-bulk-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 999
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("exportJobId").asLong();

        reportService.processQueuedExports();

        mockMvc.perform(get("/reports/exports/{jobId}", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.rowCount").value(8))
                .andExpect(jsonPath("$.preview.length()").value(5));

        String csv = mockMvc.perform(get("/reports/exports/{jobId}/download", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.lines().count()).isEqualTo(9);
        assertThat(csv).contains("expense_record_id");
        assertThat(csv).contains("9200");
        assertThat(csv).contains("9207");
    }

    @Test
    void shouldRejectExportReplayWhenIdempotencyKeyTargetsDifferentRequest() throws Exception {
        mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-conflict-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-conflict-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-26",
                                  "toDate": "2026-03-27",
                                  "limit": 1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency-Key is already used for a different export request"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report.export_job", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldHandleBurstConcurrentEquivalentExportRequestsIdempotently() throws Exception {
        String payload = """
                {
                  "dataset": "EXPENSE_FACT",
                  "format": "CSV",
                  "regionId": 1,
                  "fromDate": "2026-03-27",
                  "toDate": "2026-03-27",
                  "limit": 1
                }
                """;

        for (int attempt = 0; attempt < 3; attempt++) {
            String idempotencyKey = "export-concurrent-" + attempt;
            try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
                CountDownLatch ready = new CountDownLatch(6);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<ExportCreateResult>> futures = new ArrayList<>();
                for (int index = 0; index < 6; index++) {
                    futures.add(executor.submit(() -> createExportConcurrently(start, ready, idempotencyKey, payload)));
                }

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                List<ExportCreateResult> results = new ArrayList<>();
                for (Future<ExportCreateResult> future : futures) {
                    results.add(future.get(10, TimeUnit.SECONDS));
                }

                assertThat(results).extracting(ExportCreateResult::status).containsOnly(200);
                assertThat(results).extracting(ExportCreateResult::exportJobId).doesNotContainNull();
                assertThat(results).extracting(ExportCreateResult::exportJobId).containsOnly(results.getFirst().exportJobId());
            }
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report.export_job WHERE idempotency_key LIKE 'export-concurrent-%'",
                Integer.class
        )).isEqualTo(3);
    }

    @Test
    void shouldDenyRegionExportForEmptyNonSystemScope() throws Exception {
        mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(), false))
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowLegacyPayrollPermissionsForPayrollExportAdapter() throws Exception {
        mockMvc.perform(post("/reports/payroll/export")
                        .header("Authorization", bearer(Set.of(
                                PermissionCodes.REPORT_PAYROLL_READ,
                                PermissionCodes.REPORT_PAYROLL_EXPORT
                        ), List.of(1L), false))
                        .header("Idempotency-Key", "legacy-payroll-export")
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "fromDate": "2026-03-01",
                                  "toDate": "2026-03-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset").value("PAYROLL_SUMMARY"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void shouldSanitizeExportFailureErrorMessage() {
        jdbcTemplate.update("""
                INSERT INTO report.export_job (
                    export_job_id, idempotency_key, report_type, format, status, requested_by, requested_at, payload
                ) VALUES (
                    ?, ?, ?, ?, 'QUEUED', ?, CURRENT_TIMESTAMP, CAST(? AS jsonb)
                )
                """,
                90001L,
                "export-failure-sanitize",
                "PRIVATE_DATASET",
                "CSV",
                "system",
                """
                        {
                          "dataset": "EXPENSE_FACT",
                          "format": "CSV",
                          "regionId": 1,
                          "fromDate": "2026-03-27",
                          "toDate": "2026-03-27"
                        }
                        """
        );

        reportService.processQueuedExports();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM report.export_job WHERE export_job_id = ?",
                String.class,
                90001L
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_message FROM report.export_job WHERE export_job_id = ?",
                String.class,
                90001L
        )).isEqualTo("BadRequestException");
    }

    private ExpensePostedEvent expenseEvent(String eventId, String idempotencyKey, Long expenseRecordId, BigDecimal amount) {
        return new ExpensePostedEvent(
                eventId,
                "finance.expense.posted",
                Instant.parse("2026-03-27T08:00:00Z"),
                "finance-service",
                "corr-" + eventId,
                idempotencyKey,
                expenseRecordId,
                1L,
                101L,
                501L,
                7001L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                amount,
                "PAYROLL_RUN",
                "7001"
        );
    }

    private Set<String> reportPermissions() {
        return Set.of(PermissionCodes.REPORT_READ, PermissionCodes.REPORT_EXPORT);
    }

    private ExportCreateResult createExportConcurrently(
            CountDownLatch start,
            CountDownLatch ready,
            String idempotencyKey,
            String payload
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        var response = mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(payload))
                .andReturn()
                .getResponse();
        JsonNode body = response.getStatus() == 200 ? objectMapper.readTree(response.getContentAsString()) : null;
        return new ExportCreateResult(
                response.getStatus(),
                body == null ? null : body.get("exportJobId").asLong()
        );
    }

    private String bearer(Set<String> permissions, List<Long> regionIds, boolean systemScoped) {
        return bearer(permissions, regionIds, List.of(), systemScoped);
    }

    private String bearer(Set<String> permissions, List<Long> regionIds, List<Long> outletIds, boolean systemScoped) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        long sequence = TOKEN_SEQUENCE.incrementAndGet();
        return "Bearer " + jwtService.encode(new FernJwtClaims(
                1000L + sequence,
                "report-tester-" + sequence,
                Set.of("finance"),
                permissions,
                new ScopeRoots(systemScoped, regionIds, outletIds),
                new ScopeRoots(systemScoped, regionIds, outletIds),
                1L,
                1L,
                "report-test-jti-" + sequence,
                Instant.now(),
                Instant.now().plusSeconds(900),
                com.fern.platform.common.FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("report-service")
        ), jwtService.accessTokenTtl());
    }

    private static void ensurePosServerStarted() {
        if (posServer != null) {
            return;
        }
        try {
            startPosServer();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start report POS stub", exception);
        }
    }

    private static void ensureInventoryServerStarted() {
        if (inventoryServer != null) {
            return;
        }
        try {
            startPosServer();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start report inventory stub", exception);
        }
    }

    private static Path createExportDir() {
        try {
            return Files.createTempDirectory("fern-report-exports-test");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create export temp directory", exception);
        }
    }

    private record ExportCreateResult(int status, Long exportJobId) {
    }
}
