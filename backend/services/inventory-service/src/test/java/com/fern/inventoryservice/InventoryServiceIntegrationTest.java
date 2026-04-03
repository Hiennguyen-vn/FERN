package com.fern.inventoryservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.inventoryservice.service.InventoryEventConsumerService;
import com.fern.inventoryservice.service.StockReservationService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryServiceIntegrationTest {
    private static HttpServer orgServer;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensureOrgServerStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("inventory"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.clients.org.base-url", () -> "http://localhost:" + orgServer.getAddress().getPort());
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.inventory.reservation-ttl", () -> "60s");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private InventoryEventConsumerService inventoryEventConsumerService;

    private String token;

    @BeforeAll
    static void startOrgServer() throws IOException {
        if (orgServer != null) {
            return;
        }
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
    static void stopOrgServer() {
        if (orgServer != null) {
            orgServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    inventory.stock_reservation_line,
                    inventory.stock_reservation,
                    inventory.idempotency_request,
                    inventory.stock_count_line,
                    inventory.stock_count_session,
                    inventory.waste_record,
                    inventory.stock_adjustment,
                    inventory.inventory_transaction,
                    inventory.stock_balance,
                    inventory.availability_projection,
                    inventory.inbox_event,
                    inventory.outbox_event
                RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balance (region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, updated_at)
                VALUES (1, 101, 200, 20.0000, 0, 20.0000, 10000.00, CURRENT_TIMESTAMP)
                """);
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        token = jwtService.encode(new FernJwtClaims(
                1L,
                "inventory-tester",
                Set.of("outlet_manager"),
                Set.of(
                        "inventory.adjustment.write",
                        "inventory.waste.write",
                        "inventory.stock_count.write",
                        "inventory.stock_count.post",
                        "inventory.balance.read",
                        "inventory.ledger.read",
                        "pos.order.complete"
                ),
                new ScopeRoots(List.of(1L), List.of(101L)),
                1L,
                1L,
                "inventory-test-jti",
                Instant.now(),
                Instant.now().plusSeconds(900),
                FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("inventory-service")
        ), jwtService.accessTokenTtl());
    }

    @Test
    void shouldPostAdjustmentWasteAndStockCountFlows() throws Exception {
        String adjustmentResponse = mockMvc.perform(post("/stock-adjustments")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "ingredientId": 200,
                                  "adjustmentDirection": "IN",
                                  "qty": 5.0000,
                                  "businessDate": "2026-03-27",
                                  "reason": "CORRECTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long adjustmentId = Long.parseLong(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(adjustmentResponse).get("id").asText());

        mockMvc.perform(post("/stock-adjustments/{id}/post", adjustmentId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "adj-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(post("/waste-records")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "ingredientId": 200,
                                  "qty": 2.0000,
                                  "businessDate": "2026-03-27",
                                  "reason": "SPILL"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Long wasteId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM inventory.waste_record", Long.class);

        mockMvc.perform(post("/waste-records/{id}/post", wasteId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "waste-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        String sessionResponse = mockMvc.perform(post("/stock-count-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "countDate": "2026-03-27",
                                  "ingredientIds": [200]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = Long.parseLong(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(sessionResponse).get("id").asText());

        mockMvc.perform(post("/stock-count-sessions/{id}/start", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTING"));

        mockMvc.perform(put("/stock-count-sessions/{id}/lines", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"ingredientId": 200, "actualQty": 22.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].actualQty").value(22.0000));

        mockMvc.perform(post("/stock-count-sessions/{id}/post", sessionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "count-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        assertThat(qtyOnHand).isEqualByComparingTo("22.0000");
        assertThat(outboxPayloadField("STOCK_ADJUSTMENT", adjustmentId.toString(), "inventory.adjustment.posted", "idempotencyKey"))
                .isEqualTo("inventory.adjustment.posted:adjustment:" + adjustmentId);
        assertThat(outboxPayloadField("WASTE_RECORD", wasteId.toString(), "inventory.waste.posted", "idempotencyKey"))
                .isEqualTo("inventory.waste.posted:waste:" + wasteId);
        assertThat(outboxPayloadField("STOCK_COUNT_SESSION", sessionId.toString(), "inventory.stock_count.posted", "idempotencyKey"))
                .isEqualTo("inventory.stock_count.posted:session:" + sessionId);
    }

    @Test
    void shouldRejectCancellingAdjustmentTwice() throws Exception {
        String adjustmentResponse = mockMvc.perform(post("/stock-adjustments")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "ingredientId": 200,
                                  "adjustmentDirection": "IN",
                                  "qty": 5.0000,
                                  "businessDate": "2026-03-27",
                                  "reason": "CORRECTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long adjustmentId = Long.parseLong(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(adjustmentResponse).get("id").asText());

        mockMvc.perform(post("/stock-adjustments/{id}/cancel", adjustmentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/stock-adjustments/{id}/cancel", adjustmentId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectStartingAndCancellingStockCountSessionTwice() throws Exception {
        String sessionResponse = mockMvc.perform(post("/stock-count-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "countDate": "2026-03-27",
                                  "ingredientIds": [200]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = Long.parseLong(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(sessionResponse).get("id").asText());

        mockMvc.perform(post("/stock-count-sessions/{id}/start", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTING"));

        mockMvc.perform(post("/stock-count-sessions/{id}/start", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/stock-count-sessions/{id}/cancel", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/stock-count-sessions/{id}/cancel", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectStockAdjustmentWhenRegionDoesNotMatchOutletRoute() throws Exception {
        mockMvc.perform(post("/stock-adjustments")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 999,
                                  "outletId": 101,
                                  "ingredientId": 200,
                                  "adjustmentDirection": "IN",
                                  "qty": 5.0000,
                                  "businessDate": "2026-03-27",
                                  "reason": "CORRECTION"
                                }
                                """))
                .andExpect(status().isBadRequest());

        Integer adjustmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory.stock_adjustment", Integer.class);
        assertThat(adjustmentCount).isZero();
    }

    @Test
    void shouldRejectPostingStockAdjustmentTwiceWithDifferentIdempotencyKeys() throws Exception {
        String adjustmentResponse = mockMvc.perform(post("/stock-adjustments")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "ingredientId": 200,
                                  "adjustmentDirection": "IN",
                                  "qty": 5.0000,
                                  "businessDate": "2026-03-27",
                                  "reason": "CORRECTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long adjustmentId = Long.parseLong(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(adjustmentResponse).get("id").asText());

        mockMvc.perform(post("/stock-adjustments/{id}/post", adjustmentId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "adj-replay-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(post("/stock-adjustments/{id}/post", adjustmentId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "adj-replay-2"))
                .andExpect(status().isConflict());

        Integer transactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_ADJUSTMENT' AND source_reference_id = ?
                """, Integer.class, adjustmentId.toString());
        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);

        assertThat(transactionCount).isEqualTo(1);
        assertThat(qtyOnHand).isEqualByComparingTo("25.0000");
    }

    @Test
    void shouldRejectPostingDifferentStockAdjustmentWithSameIdempotencyKey() throws Exception {
        Long firstAdjustmentId = createStockAdjustment("IN", "5.0000", "FIRST");
        Long secondAdjustmentId = createStockAdjustment("IN", "4.0000", "SECOND");

        postStockAdjustment(firstAdjustmentId, "adj-cross-resource");

        mockMvc.perform(post("/stock-adjustments/{id}/post", secondAdjustmentId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "adj-cross-resource"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency-Key is already used for a different stock adjustment"));

        Integer firstTransactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_ADJUSTMENT' AND source_reference_id = ?
                """, Integer.class, firstAdjustmentId.toString());
        Integer secondTransactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_ADJUSTMENT' AND source_reference_id = ?
                """, Integer.class, secondAdjustmentId.toString());
        String secondStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_adjustment
                WHERE id = ?
                """, String.class, secondAdjustmentId);

        assertThat(firstTransactionCount).isEqualTo(1);
        assertThat(secondTransactionCount).isZero();
        assertThat(secondStatus).isEqualTo("DRAFT");
    }

    @Test
    void shouldRejectPostingDifferentWasteRecordWithSameIdempotencyKey() throws Exception {
        Long firstWasteId = createWasteRecord("2.0000", "FIRST");
        Long secondWasteId = createWasteRecord("1.5000", "SECOND");

        postWasteRecord(firstWasteId, "waste-cross-resource");

        mockMvc.perform(post("/waste-records/{id}/post", secondWasteId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "waste-cross-resource"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency-Key is already used for a different waste record"));

        Integer firstTransactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'WASTE_RECORD' AND source_reference_id = ?
                """, Integer.class, firstWasteId.toString());
        Integer secondTransactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'WASTE_RECORD' AND source_reference_id = ?
                """, Integer.class, secondWasteId.toString());
        String secondStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.waste_record
                WHERE id = ?
                """, String.class, secondWasteId);

        assertThat(firstTransactionCount).isEqualTo(1);
        assertThat(secondTransactionCount).isZero();
        assertThat(secondStatus).isEqualTo("DRAFT");
    }

    @Test
    void shouldRejectPostingDifferentStockCountSessionWithSameIdempotencyKey() throws Exception {
        Long firstSessionId = createStockCountSession();
        startStockCountSession(firstSessionId);
        updateStockCountLines(firstSessionId, "21.0000");

        Long secondSessionId = createStockCountSession();
        startStockCountSession(secondSessionId);
        updateStockCountLines(secondSessionId, "23.0000");

        postStockCountSession(firstSessionId, "count-cross-resource");

        mockMvc.perform(post("/stock-count-sessions/{id}/post", secondSessionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "count-cross-resource"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency-Key is already used for a different stock count session"));

        Integer firstTransactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_COUNT_SESSION' AND source_reference_id = ?
                """, Integer.class, firstSessionId.toString());
        Integer secondTransactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_COUNT_SESSION' AND source_reference_id = ?
                """, Integer.class, secondSessionId.toString());
        String secondStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_count_session
                WHERE id = ?
                """, String.class, secondSessionId);

        assertThat(firstTransactionCount).isEqualTo(1);
        assertThat(secondTransactionCount).isZero();
        assertThat(secondStatus).isEqualTo("COUNTING");
    }

    @Test
    void shouldRequireActualQtyBeforePostingStockCountSession() throws Exception {
        Long sessionId = createStockCountSession(List.of(200L, 201L));
        startStockCountSession(sessionId);

        mockMvc.perform(post("/stock-count-sessions/{id}/post", sessionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "count-missing-actual"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("All stock count lines must have actual quantity before posting"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_COUNT_SESSION'
                  AND source_reference_id = ?
                """, Integer.class, sessionId.toString())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_count_session
                WHERE id = ?
                """, String.class, sessionId)).isEqualTo("COUNTING");
    }

    @Test
    void shouldRejectUpdatingStockCountLinesAfterSessionPosted() throws Exception {
        String sessionResponse = mockMvc.perform(post("/stock-count-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "countDate": "2026-03-27",
                                  "ingredientIds": [200]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long sessionId = Long.parseLong(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(sessionResponse).get("id").asText());

        mockMvc.perform(post("/stock-count-sessions/{id}/start", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/stock-count-sessions/{id}/lines", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"ingredientId": 200, "actualQty": 21.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/stock-count-sessions/{id}/post", sessionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "count-update-block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(put("/stock-count-sessions/{id}/lines", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"ingredientId": 200, "actualQty": 25.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldKeepStockBalanceSnapshotAlignedWithLedgerAfterMixedSources() throws Exception {
        Long adjustmentId = createStockAdjustment("IN", "4.0000", "ledger-correction");
        postStockAdjustment(adjustmentId, "adj-ledger-check");

        Long wasteId = createWasteRecord("2.0000", "SPILL");
        postWasteRecord(wasteId, "waste-ledger-check");

        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5301L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("3.0000")))
                )
        );
        inventoryEventConsumerService.consumeSaleCompleted(new PosSaleCompletedEvent(
                "sale-event-ledger-check",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:00:00Z"),
                "pos-service",
                "corr-sale-ledger-check",
                "idem-sale-ledger-check",
                5301L,
                7301L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:00:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("3.0000")))
        ));
        inventoryEventConsumerService.consumeGoodsReceiptPosted(goodsReceiptEvent(
                "receipt-event-ledger-check",
                "idem-gr-ledger-check",
                9301L,
                new BigDecimal("6.0000"),
                Instant.parse("2026-03-27T11:00:00Z")
        ));

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal qtyReserved = jdbcTemplate.queryForObject("""
                SELECT qty_reserved
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal qtyAvailable = jdbcTemplate.queryForObject("""
                SELECT qty_available
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal ledgerDelta = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);

        assertThat(qtyOnHand).isEqualByComparingTo("25.0000");
        assertThat(qtyOnHand).isEqualByComparingTo(new BigDecimal("20.0000").add(ledgerDelta));
        assertThat(qtyReserved).isEqualByComparingTo("0.0000");
        assertThat(qtyAvailable).isEqualByComparingTo(qtyOnHand.subtract(qtyReserved));
    }

    @Test
    void shouldPreserveLaterGoodsReceiptWhenPostingStockCountStartedBeforeReceipt() throws Exception {
        Long sessionId = createStockCountSession();
        startStockCountSession(sessionId);

        inventoryEventConsumerService.consumeGoodsReceiptPosted(goodsReceiptEvent(
                "receipt-event-during-count",
                "idem-gr-during-count",
                9302L,
                new BigDecimal("5.0000"),
                Instant.parse("2026-03-27T10:30:00Z")
        ));

        mockMvc.perform(put("/stock-count-sessions/{id}/lines", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"ingredientId": 200, "actualQty": 20.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].systemQty").value(20.0000))
                .andExpect(jsonPath("$.lines[0].actualQty").value(20.0000));

        mockMvc.perform(post("/stock-count-sessions/{id}/post", sessionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "count-after-gr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer goodsReceiptTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN'
                  AND source_reference_id = '9302'
                """, Integer.class);
        Integer stockCountTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_COUNT_SESSION'
                  AND source_reference_id = ?
                """, Integer.class, sessionId.toString());

        assertThat(qtyOnHand).isEqualByComparingTo("25.0000");
        assertThat(goodsReceiptTxnCount).isEqualTo(1);
        assertThat(stockCountTxnCount).isZero();
    }

    @Test
    void shouldProcessConcurrentGoodsReceiptAndWasteWithoutDriftingBalance() throws Exception {
        Long wasteId = createWasteRecord("4.0000", "CONCURRENT_GR_WASTE");
        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "receipt-event-concurrent-waste",
                "idem-gr-concurrent-waste",
                9305L,
                new BigDecimal("6.0000"),
                Instant.parse("2026-03-27T12:30:00Z")
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> receipt = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeGoodsReceiptPosted(event);
                return null;
            });
            Future<Integer> wastePost = executor.submit(
                    () -> concurrentInventoryPostStatus(start, ready, "/waste-records/%d/post".formatted(wasteId), "waste-concurrent-gr")
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            receipt.get(10, TimeUnit.SECONDS);
            assertThat(wastePost.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal ledgerDelta = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer purchaseInCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9305'
                """, Integer.class);
        Integer wasteCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'WASTE_OUT' AND source_reference_id = ?
                """, Integer.class, wasteId.toString());

        assertThat(qtyOnHand).isEqualByComparingTo("22.0000");
        assertThat(qtyOnHand).isEqualByComparingTo(new BigDecimal("20.0000").add(ledgerDelta));
        assertThat(purchaseInCount).isEqualTo(1);
        assertThat(wasteCount).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlySerializableOutcomeWhenGoodsReceiptRacesNegativeAdjustment() throws Exception {
        Long adjustmentId = createStockAdjustment("OUT", "25.0000", "CONCURRENT_GR_NEGATIVE");
        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "receipt-event-concurrent-adjustment",
                "idem-gr-concurrent-adjustment",
                9306L,
                new BigDecimal("6.0000"),
                Instant.parse("2026-03-27T12:45:00Z")
        );

        int adjustmentStatus;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> receipt = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeGoodsReceiptPosted(event);
                return null;
            });
            Future<Integer> adjustmentPost = executor.submit(
                    () -> concurrentInventoryPostStatus(
                            start,
                            ready,
                            "/stock-adjustments/%d/post".formatted(adjustmentId),
                            "adj-concurrent-gr-negative"
                    )
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            receipt.get(10, TimeUnit.SECONDS);
            adjustmentStatus = adjustmentPost.get(10, TimeUnit.SECONDS);
        }

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal ledgerDelta = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer purchaseInCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9306'
                """, Integer.class);
        Integer adjustmentTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_ADJUSTMENT' AND source_reference_id = ?
                """, Integer.class, adjustmentId.toString());
        Integer postedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_adjustment
                WHERE id = ?
                  AND status = 'POSTED'
                """, Integer.class, adjustmentId);

        assertThat(adjustmentStatus).isIn(200, 409);
        assertThat(purchaseInCount).isEqualTo(1);
        assertThat(qtyOnHand).isEqualByComparingTo(new BigDecimal("20.0000").add(ledgerDelta));
        if (adjustmentStatus == 200) {
            assertThat(qtyOnHand).isEqualByComparingTo("1.0000");
            assertThat(adjustmentTxnCount).isEqualTo(1);
            assertThat(postedCount).isEqualTo(1);
        } else {
            assertThat(qtyOnHand).isEqualByComparingTo("26.0000");
            assertThat(adjustmentTxnCount).isZero();
            assertThat(postedCount).isZero();
        }
    }

    @Test
    void shouldAvoidDeadlockWhenMultiLineGoodsReceiptRacesStockCountPosting() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balance (region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, updated_at)
                VALUES (1, 101, 201, 10.0000, 0, 10.0000, 12000.00, CURRENT_TIMESTAMP)
                """);

        Long sessionId = createStockCountSession(List.of(201L, 200L));
        startStockCountSession(sessionId);
        mockMvc.perform(put("/stock-count-sessions/{id}/lines", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"ingredientId": 200, "actualQty": 18.0000},
                                    {"ingredientId": 201, "actualQty": 8.0000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "receipt-event-concurrent-stock-count",
                "idem-gr-concurrent-stock-count",
                9307L,
                Instant.parse("2026-03-27T13:00:00Z"),
                List.of(
                        new GoodsReceiptPostedLine(201L, new BigDecimal("5.0000"), new BigDecimal("12000.00"), 3302L),
                        new GoodsReceiptPostedLine(200L, new BigDecimal("4.0000"), new BigDecimal("10000.00"), 3301L)
                )
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> receipt = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeGoodsReceiptPosted(event);
                return null;
            });
            Future<Integer> stockCountPost = executor.submit(
                    () -> concurrentInventoryPostStatus(
                            start,
                            ready,
                            "/stock-count-sessions/%d/post".formatted(sessionId),
                            "count-concurrent-gr"
                    )
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            receipt.get(10, TimeUnit.SECONDS);
            assertThat(stockCountPost.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }

        BigDecimal qtyOnHand200 = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal qtyOnHand201 = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 201
                """, BigDecimal.class);
        BigDecimal ledgerDelta200 = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal ledgerDelta201 = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = 201
                """, BigDecimal.class);
        Integer purchaseInCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9307'
                """, Integer.class);
        Integer stockCountTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_COUNT_SESSION'
                  AND source_reference_id = ?
                """, Integer.class, sessionId.toString());

        assertThat(qtyOnHand200).isEqualByComparingTo("22.0000");
        assertThat(qtyOnHand201).isEqualByComparingTo("13.0000");
        assertThat(qtyOnHand200).isEqualByComparingTo(new BigDecimal("20.0000").add(ledgerDelta200));
        assertThat(qtyOnHand201).isEqualByComparingTo(new BigDecimal("10.0000").add(ledgerDelta201));
        assertThat(purchaseInCount).isEqualTo(2);
        assertThat(stockCountTxnCount).isEqualTo(2);
    }

    @Test
    void shouldProcessConcurrentAdjustmentAndWasteForSameItemWithoutBreakingBalance() throws Exception {
        Long adjustmentId = createStockAdjustment("IN", "7.0000", "CONCURRENT_IN");
        Long wasteId = createWasteRecord("4.0000", "CONCURRENT_WASTE");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> adjustmentPost = executor.submit(
                    () -> concurrentInventoryPostStatus(start, ready, "/stock-adjustments/%d/post".formatted(adjustmentId), "adj-concurrent")
            );
            Future<Integer> wastePost = executor.submit(
                    () -> concurrentInventoryPostStatus(start, ready, "/waste-records/%d/post".formatted(wasteId), "waste-concurrent")
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(adjustmentPost.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(wastePost.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal ledgerDelta = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer adjustmentTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_ADJUSTMENT' AND source_reference_id = ?
                """, Integer.class, adjustmentId.toString());
        Integer wasteTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'WASTE_RECORD' AND source_reference_id = ?
                """, Integer.class, wasteId.toString());

        assertThat(qtyOnHand).isEqualByComparingTo("23.0000");
        assertThat(qtyOnHand).isEqualByComparingTo(new BigDecimal("20.0000").add(ledgerDelta));
        assertThat(adjustmentTxnCount).isEqualTo(1);
        assertThat(wasteTxnCount).isEqualTo(1);
    }

    @Test
    void shouldRejectConcurrentNegativeAdjustmentAndWasteThatWouldOverdrawBalance() throws Exception {
        Long adjustmentId = createStockAdjustment("OUT", "15.0000", "CONCURRENT_NEGATIVE_OUT");
        Long wasteId = createWasteRecord("15.0000", "CONCURRENT_NEGATIVE_WASTE");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> adjustmentPost = executor.submit(
                    () -> concurrentInventoryPostStatus(start, ready, "/stock-adjustments/%d/post".formatted(adjustmentId), "adj-concurrent-negative")
            );
            Future<Integer> wastePost = executor.submit(
                    () -> concurrentInventoryPostStatus(start, ready, "/waste-records/%d/post".formatted(wasteId), "waste-concurrent-negative")
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(adjustmentPost.get(10, TimeUnit.SECONDS), wastePost.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer successfulTxnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type IN ('STOCK_ADJUSTMENT', 'WASTE_RECORD')
                  AND source_reference_id IN (?, ?)
                """, Integer.class, adjustmentId.toString(), wasteId.toString());
        Integer postedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT status FROM inventory.stock_adjustment WHERE id = ?
                    UNION ALL
                    SELECT status FROM inventory.waste_record WHERE id = ?
                ) states
                WHERE status = 'POSTED'
                """, Integer.class, adjustmentId, wasteId);

        assertThat(qtyOnHand).isEqualByComparingTo("5.0000");
        assertThat(successfulTxnCount).isEqualTo(1);
        assertThat(postedCount).isEqualTo(1);
    }

    @Test
    void shouldPreventConcurrentStockAdjustmentPostsFromDifferentResourcesSharingIdempotencyKey() throws Exception {
        Long firstAdjustmentId = createStockAdjustment("IN", "7.0000", "CONCURRENT_FIRST");
        Long secondAdjustmentId = createStockAdjustment("IN", "6.0000", "CONCURRENT_SECOND");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> firstPost = executor.submit(
                    () -> concurrentInventoryPostStatus(
                            start,
                            ready,
                            "/stock-adjustments/%d/post".formatted(firstAdjustmentId),
                            "adj-concurrent-shared-key"
                    )
            );
            Future<Integer> secondPost = executor.submit(
                    () -> concurrentInventoryPostStatus(
                            start,
                            ready,
                            "/stock-adjustments/%d/post".formatted(secondAdjustmentId),
                            "adj-concurrent-shared-key"
                    )
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(firstPost.get(10, TimeUnit.SECONDS), secondPost.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }

        Integer transactionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE source_reference_type = 'STOCK_ADJUSTMENT'
                  AND source_reference_id IN (?, ?)
                """, Integer.class, firstAdjustmentId.toString(), secondAdjustmentId.toString());
        Integer postedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_adjustment
                WHERE id IN (?, ?)
                  AND status = 'POSTED'
                """, Integer.class, firstAdjustmentId, secondAdjustmentId);
        Integer draftCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_adjustment
                WHERE id IN (?, ?)
                  AND status = 'DRAFT'
                """, Integer.class, firstAdjustmentId, secondAdjustmentId);
        Integer idempotencyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.idempotency_request
                WHERE operation = 'stock-adjustment-post'
                  AND idempotency_key = 'adj-concurrent-shared-key'
                """, Integer.class);

        assertThat(transactionCount).isEqualTo(1);
        assertThat(postedCount).isEqualTo(1);
        assertThat(draftCount).isEqualTo(1);
        assertThat(idempotencyCount).isEqualTo(1);
    }

    @Test
    void shouldConsumeConcurrentReplayOfSameGoodsReceiptEventOnlyOnce() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "receipt-event-concurrent-replay",
                "idem-gr-concurrent-replay",
                9303L,
                new BigDecimal("6.0000"),
                Instant.parse("2026-03-27T11:30:00Z")
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeGoodsReceiptPosted(event);
                return null;
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeGoodsReceiptPosted(event);
                return null;
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Integer inboxCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inbox_event
                WHERE source_event_id = 'receipt-event-concurrent-replay'
                """, Integer.class);
        Integer purchaseInCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9303'
                """, Integer.class);
        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);

        assertThat(inboxCount).isEqualTo(1);
        assertThat(purchaseInCount).isEqualTo(1);
        assertThat(qtyOnHand).isEqualByComparingTo("26.0000");
    }

    @Test
    void shouldIgnoreBurstDuplicateGoodsReceiptReplayWithoutDriftingStockBalance() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = goodsReceiptEvent(
                "receipt-event-burst-replay",
                "idem-gr-burst-replay",
                9304L,
                new BigDecimal("4.0000"),
                Instant.parse("2026-03-27T12:00:00Z")
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            CountDownLatch ready = new CountDownLatch(5);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, 5)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        inventoryEventConsumerService.consumeGoodsReceiptPosted(event);
                        return (Object) null;
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        Integer inboxCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inbox_event
                WHERE source_event_id = 'receipt-event-burst-replay'
                """, Integer.class);
        Integer purchaseInCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9304'
                """, Integer.class);
        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);

        assertThat(inboxCount).isEqualTo(1);
        assertThat(purchaseInCount).isEqualTo(1);
        assertThat(qtyOnHand).isEqualByComparingTo("24.0000");
    }

    @Test
    void shouldReleaseReservationAndRestoreAvailability() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5002L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000")))
                )
        );

        stockReservationService.releaseSaleReservation(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RELEASE)),
                reservation.reservationId()
        );

        String reservationStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_reservation
                WHERE id = ?
                """, String.class, reservation.reservationId());
        BigDecimal qtyReserved = jdbcTemplate.queryForObject("""
                SELECT qty_reserved
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal qtyAvailable = jdbcTemplate.queryForObject("""
                SELECT qty_available
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);

        assertThat(reservationStatus).isEqualTo("CANCELLED");
        assertThat(qtyReserved).isEqualByComparingTo("0.0000");
        assertThat(qtyAvailable).isEqualByComparingTo("20.0000");
    }

    @Test
    void shouldReuseExpiredReservationWithoutDoubleCountingStock() {
        SaleReservationResponse initialReservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5003L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000")))
                )
        );
        jdbcTemplate.update("""
                UPDATE inventory.stock_reservation
                SET expires_at = ?
                WHERE id = ?
                """,
                java.time.OffsetDateTime.ofInstant(Instant.now().minusSeconds(60), java.time.ZoneOffset.UTC),
                initialReservation.reservationId());

        SaleReservationResponse renewedReservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5003L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000")))
                )
        );

        String reservationStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_reservation
                WHERE id = ?
                """, String.class, renewedReservation.reservationId());
        BigDecimal qtyReserved = jdbcTemplate.queryForObject("""
                SELECT qty_reserved
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer lineCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_reservation_line
                WHERE reservation_id = ?
                """, Integer.class, renewedReservation.reservationId());

        assertThat(renewedReservation.reservationId()).isEqualTo(initialReservation.reservationId());
        assertThat(reservationStatus).isEqualTo("RESERVED");
        assertThat(qtyReserved).isEqualByComparingTo("5.0000");
        assertThat(lineCount).isEqualTo(1);
    }

    @Test
    void shouldConsumeSaleAndGoodsReceiptEventsIdempotently() throws Exception {
        stockReservationService.reserveSale(new com.fern.platform.common.FernPrincipal(
                1L,
                "inventory-tester",
                Set.of("inventory-service"),
                Set.of("inventory.internal.reserve"),
                new ScopeRoots(List.of(1L), List.of(101L)),
                1L,
                1L,
                "reservation-jti",
                FernPrincipalType.SERVICE
        ), new com.fern.platform.contracts.SaleReservationRequest(
                101L,
                LocalDate.of(2026, 3, 27),
                5001L,
                List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000")))
        ));
        Long reservationId = jdbcTemplate.queryForObject("SELECT id FROM inventory.stock_reservation WHERE source_order_id = 5001", Long.class);

        PosSaleCompletedEvent saleEvent = new PosSaleCompletedEvent(
                "sale-event-1",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:00:00Z"),
                "pos-service",
                "corr-sale-1",
                "idem-sale-1",
                5001L,
                7001L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:00:00Z"),
                1L,
                reservationId,
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000")))
        );
        inventoryEventConsumerService.consumeSaleCompleted(saleEvent);
        inventoryEventConsumerService.consumeSaleCompleted(saleEvent);

        ProcurementGoodsReceiptPostedEvent receiptEvent = new ProcurementGoodsReceiptPostedEvent(
                "receipt-event-1",
                "procurement.goods_receipt.posted",
                Instant.parse("2026-03-27T11:00:00Z"),
                "procurement-service",
                "corr-gr-1",
                "idem-gr-1",
                9001L,
                8001L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T11:00:00Z"),
                2L,
                List.of(new com.fern.platform.contracts.GoodsReceiptPostedLine(200L, new BigDecimal("8.0000"), new BigDecimal("10000.00"), 3001L))
        );
        inventoryEventConsumerService.consumeGoodsReceiptPosted(receiptEvent);
        inventoryEventConsumerService.consumeGoodsReceiptPosted(receiptEvent);

        Integer saleUsageCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'SALE_USAGE' AND source_reference_id = '5001'
                """, Integer.class);
        Integer purchaseInCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9001'
                """, Integer.class);
        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);

        assertThat(saleUsageCount).isEqualTo(1);
        assertThat(purchaseInCount).isEqualTo(1);
        assertThat(qtyOnHand).isEqualByComparingTo("23.0000");

        mockMvc.perform(get("/inventory-transactions")
                        .header("Authorization", bearer())
                        .param("outletId", "101")
                        .param("ingredientId", "200")
                        .param("txnType", "SALE_USAGE")
                        .param("sourceType", "SALE_ORDER")
                        .param("sourceId", "5001")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].txnType").value("SALE_USAGE"))
                .andExpect(jsonPath("$.items[0].sourceReferenceId").value("5001"));
    }

    @Test
    void shouldCommitSaleUsingReservedQuantitiesInsteadOfEventPayload() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5401L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
                )
        );

        inventoryEventConsumerService.consumeSaleCompleted(new PosSaleCompletedEvent(
                "sale-event-reservation-source",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:15:00Z"),
                "pos-service",
                "corr-sale-reservation-source",
                "idem-sale-reservation-source",
                5401L,
                7401L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:15:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("9.0000")))
        ));

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal saleUsageQty = jdbcTemplate.queryForObject("""
                SELECT qty_change
                FROM inventory.inventory_transaction
                WHERE txn_type = 'SALE_USAGE' AND source_reference_id = '5401'
                """, BigDecimal.class);

        assertThat(qtyOnHand).isEqualByComparingTo("18.0000");
        assertThat(saleUsageQty).isEqualByComparingTo("-2.0000");
    }

    @Test
    void shouldRejectSaleCompletionWhenReservationDoesNotMatchSaleOrder() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5402L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
                )
        );

        PosSaleCompletedEvent mismatchedEvent = new PosSaleCompletedEvent(
                "sale-event-mismatch",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:20:00Z"),
                "pos-service",
                "corr-sale-mismatch",
                "idem-sale-mismatch",
                9999L,
                7402L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:20:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
        );

        assertThatThrownBy(() -> inventoryEventConsumerService.consumeSaleCompleted(mismatchedEvent))
                .isInstanceOf(com.fern.platform.common.ConflictException.class)
                .hasMessage("Reservation source order does not match pos.sale.completed sale order");

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        String reservationStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_reservation
                WHERE id = ?
                """, String.class, reservation.reservationId());

        assertThat(qtyOnHand).isEqualByComparingTo("20.0000");
        assertThat(reservationStatus).isEqualTo("RESERVED");
    }

    @Test
    void shouldRejectSaleCompletionWhenReservationCommitWouldDriveReservedStockNegative() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5403L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
                )
        );
        jdbcTemplate.update("""
                UPDATE inventory.stock_balance
                SET qty_on_hand = 1.0000,
                    qty_reserved = 1.0000,
                    qty_available = 0.0000,
                    updated_at = CURRENT_TIMESTAMP
                WHERE outlet_id = 101 AND ingredient_id = 200
                """);

        PosSaleCompletedEvent event = new PosSaleCompletedEvent(
                "sale-event-negative-guard",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:25:00Z"),
                "pos-service",
                "corr-sale-negative-guard",
                "idem-sale-negative-guard",
                5403L,
                7403L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:25:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
        );

        assertThatThrownBy(() -> inventoryEventConsumerService.consumeSaleCompleted(event))
                .isInstanceOf(com.fern.platform.common.ConflictException.class)
                .hasMessage("Reservation commit failed for ingredient 200");

        BigDecimal qtyOnHand = jdbcTemplate.queryForObject("""
                SELECT qty_on_hand
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal qtyReserved = jdbcTemplate.queryForObject("""
                SELECT qty_reserved
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer saleUsageCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'SALE_USAGE' AND source_reference_id = '5403'
                """, Integer.class);

        assertThat(qtyOnHand).isEqualByComparingTo("1.0000");
        assertThat(qtyReserved).isEqualByComparingTo("1.0000");
        assertThat(saleUsageCount).isZero();
    }

    @Test
    void shouldRejectSaleCompletionForCancelledReservation() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5404L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
                )
        );
        stockReservationService.releaseSaleReservation(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RELEASE)),
                reservation.reservationId()
        );

        PosSaleCompletedEvent event = new PosSaleCompletedEvent(
                "sale-event-cancelled-reservation",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:26:00Z"),
                "pos-service",
                "corr-sale-cancelled-reservation",
                "idem-sale-cancelled-reservation",
                5404L,
                7404L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:26:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
        );

        assertThatThrownBy(() -> inventoryEventConsumerService.consumeSaleCompleted(event))
                .isInstanceOf(com.fern.platform.common.ConflictException.class)
                .hasMessage("Reservation is not active");

        String reservationStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_reservation
                WHERE id = ?
                """, String.class, reservation.reservationId());
        Integer saleUsageCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'SALE_USAGE' AND source_reference_id = '5404'
                """, Integer.class);

        assertThat(reservationStatus).isEqualTo("CANCELLED");
        assertThat(saleUsageCount).isZero();
    }

    @Test
    void shouldUseReservationBusinessDateForSaleUsageCommit() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5405L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
                )
        );

        inventoryEventConsumerService.consumeSaleCompleted(new PosSaleCompletedEvent(
                "sale-event-business-date-drift",
                "pos.sale.completed",
                Instant.parse("2026-03-28T00:05:00Z"),
                "pos-service",
                "corr-sale-business-date-drift",
                "idem-sale-business-date-drift",
                5405L,
                7405L,
                1L,
                101L,
                LocalDate.of(2026, 3, 28),
                Instant.parse("2026-03-28T00:05:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("99.0000")))
        ));

        LocalDate transactionBusinessDate = jdbcTemplate.queryForObject("""
                SELECT business_date
                FROM inventory.inventory_transaction
                WHERE txn_type = 'SALE_USAGE' AND source_reference_id = '5405'
                """, LocalDate.class);
        String outboxBusinessDate = jdbcTemplate.queryForObject("""
                SELECT payload ->> 'businessDate'
                FROM inventory.outbox_event
                WHERE aggregate_type = 'SALE_ORDER'
                  AND aggregate_id = '5405:ingredient:200'
                """, String.class);

        assertThat(transactionBusinessDate).isEqualTo(LocalDate.of(2026, 3, 27));
        assertThat(outboxBusinessDate).isEqualTo("2026-03-27");
    }

    @Test
    void shouldExposeInventoryOutletCloseCheckForInternalCallers() throws Exception {
        stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5406L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("1.0000")))
                )
        );
        mockMvc.perform(post("/stock-count-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "countDate": "2026-03-27",
                                  "ingredientIds": [200]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/internal/inventory/outlet-close-check")
                        .header("Authorization", serviceBearer(Set.of(PermissionCodes.INVENTORY_INTERNAL_READ)))
                        .param("outletId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(101))
                .andExpect(jsonPath("$.blockingReservations").value(1))
                .andExpect(jsonPath("$.blockingStockCountSessions").value(1))
                .andExpect(jsonPath("$.hasBlockingOperations").value(true));
    }

    @Test
    void shouldSanitizeInboxFailureMessage() {
        PosSaleCompletedEvent invalidEvent = new PosSaleCompletedEvent(
                "sale-event-failed",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:30:00Z"),
                "pos-service",
                "corr-sale-failed",
                "idem-sale-failed",
                5002L,
                7002L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:30:00Z"),
                1L,
                null,
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("1.0000")))
        );

        assertThatThrownBy(() -> inventoryEventConsumerService.consumeSaleCompleted(invalidEvent))
                .isInstanceOf(com.fern.platform.common.BadRequestException.class)
                .hasMessage("Missing reservationId on pos.sale.completed");

        String errorMessage = jdbcTemplate.queryForObject("""
                SELECT error_message
                FROM inventory.inbox_event
                WHERE source_event_id = 'sale-event-failed'
                """, String.class);
        assertThat(errorMessage).isEqualTo("BadRequestException");
    }

    @Test
    void shouldPaginateStockBalancesAndInventoryTransactions() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balance (region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, updated_at)
                VALUES
                    (1, 101, 201, 5.0000, 0, 5.0000, 8000.00, CURRENT_TIMESTAMP),
                    (1, 101, 202, 7.0000, 0, 7.0000, 9000.00, CURRENT_TIMESTAMP),
                    (1, 101, 203, 9.0000, 0, 9.0000, 9500.00, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO inventory.inventory_transaction (
                    region_id, outlet_id, ingredient_id, qty_change, business_date, txn_time, txn_type, unit_cost,
                    source_reference_type, source_reference_id, created_by_user_id, created_at
                ) VALUES
                    (1, 101, 200, 2.0000, DATE '2026-03-27', CURRENT_TIMESTAMP - INTERVAL '3 minute', 'PURCHASE_IN', 10000.00, 'GOODS_RECEIPT', '9001', 1, CURRENT_TIMESTAMP),
                    (1, 101, 201, -1.0000, DATE '2026-03-27', CURRENT_TIMESTAMP - INTERVAL '2 minute', 'WASTE_OUT', 8000.00, 'WASTE_RECORD', '5001', 1, CURRENT_TIMESTAMP),
                    (1, 101, 202, 4.0000, DATE '2026-03-27', CURRENT_TIMESTAMP - INTERVAL '1 minute', 'STOCK_ADJUSTMENT_IN', 9000.00, 'STOCK_ADJUSTMENT', '4001', 1, CURRENT_TIMESTAMP)
                """);

        mockMvc.perform(get("/stock-balances")
                        .header("Authorization", bearer())
                        .param("outletId", "101")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].ingredientId").value(200))
                .andExpect(jsonPath("$.items[1].ingredientId").value(201));

        mockMvc.perform(get("/inventory-transactions")
                        .header("Authorization", bearer())
                        .param("outletId", "101")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void shouldRejectInventoryListPageSizeAboveLimit() throws Exception {
        mockMvc.perform(get("/stock-balances")
                        .header("Authorization", bearer())
                        .param("outletId", "101")
                        .param("size", "201"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectStockBalanceQueryOutsideOutletScope() throws Exception {
        mockMvc.perform(get("/stock-balances")
                        .header("Authorization", bearerForOutlets(List.of(101L)))
                        .param("outletId", "201")
                        .param("size", "20"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/inventory-transactions")
                        .header("Authorization", bearerForOutlets(List.of(101L)))
                        .param("outletId", "201")
                        .param("size", "20"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldUseConfiguredReservationTtl() {
        Instant before = Instant.now();
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5004L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("1.0000")))
                )
        );
        Instant after = Instant.now();

        assertThat(reservation.expiresAt()).isAfterOrEqualTo(before.plusSeconds(60));
        assertThat(reservation.expiresAt()).isBeforeOrEqualTo(after.plusSeconds(60));
    }

    @Test
    void shouldRejectReserveSaleFromUserPrincipalEvenWithPermission() {
        FernPrincipal userPrincipal = new FernPrincipal(
                1L,
                "inventory-user",
                Set.of("outlet_manager"),
                Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE),
                new ScopeRoots(List.of(1L), List.of(101L)),
                1L,
                1L,
                "inventory-user-jti",
                FernPrincipalType.USER
        );

        assertThatThrownBy(() -> stockReservationService.reserveSale(
                userPrincipal,
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5101L,
                        List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("1.0000")))
                )
        ))
                .isInstanceOf(com.fern.platform.common.ForbiddenException.class)
                .hasMessage("Service principal is required");
    }

    @Test
    void shouldPreventConcurrentReservationsFromOversellingStock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> concurrentReservationOutcome(start, ready, 5201L));
            Future<String> second = executor.submit(() -> concurrentReservationOutcome(start, ready, 5202L));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            String firstOutcome = first.get(10, TimeUnit.SECONDS);
            String secondOutcome = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstOutcome, secondOutcome)).containsExactlyInAnyOrder("RESERVED", "CONFLICT");
        } finally {
            executor.shutdownNow();
        }

        BigDecimal qtyReserved = jdbcTemplate.queryForObject("""
                SELECT qty_reserved
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        BigDecimal qtyAvailable = jdbcTemplate.queryForObject("""
                SELECT qty_available
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = 200
                """, BigDecimal.class);
        Integer reservationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_reservation
                WHERE source_order_id IN (5201, 5202)
                """, Integer.class);

        assertThat(qtyReserved).isEqualByComparingTo("15.0000");
        assertThat(qtyAvailable).isEqualByComparingTo("5.0000");
        assertThat(reservationCount).isEqualTo(1);
    }

    @Test
    void shouldProcessConcurrentMultiIngredientReservationsWithoutDeadlockOrDrift() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balance (region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, updated_at)
                VALUES (1, 101, 201, 20.0000, 0, 20.0000, 12000.00, CURRENT_TIMESTAMP)
                """);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Long> first = executor.submit(() -> concurrentMultiIngredientReservation(
                    start,
                    ready,
                    5301L,
                    List.of(
                            new com.fern.platform.contracts.SaleReservationItem(201L, "ING-201", "Sugar", "KG", new BigDecimal("5.0000")),
                            new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000"))
                    )
            ));
            Future<Long> second = executor.submit(() -> concurrentMultiIngredientReservation(
                    start,
                    ready,
                    5302L,
                    List.of(
                            new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("5.0000")),
                            new com.fern.platform.contracts.SaleReservationItem(201L, "ING-201", "Sugar", "KG", new BigDecimal("5.0000"))
                    )
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull();
            assertThat(second.get(10, TimeUnit.SECONDS)).isNotNull();
        }

        assertThat(balanceField(200L, "qty_reserved")).isEqualByComparingTo("10.0000");
        assertThat(balanceField(200L, "qty_available")).isEqualByComparingTo("10.0000");
        assertThat(balanceField(201L, "qty_reserved")).isEqualByComparingTo("10.0000");
        assertThat(balanceField(201L, "qty_available")).isEqualByComparingTo("10.0000");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.stock_reservation
                WHERE source_order_id IN (5301, 5302)
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldProcessConcurrentSaleCompletionAndGoodsReceiptAcrossMultipleIngredientsWithoutDeadlock() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balance (region_id, outlet_id, ingredient_id, qty_on_hand, qty_reserved, qty_available, unit_cost, updated_at)
                VALUES (1, 101, 201, 10.0000, 0, 10.0000, 12000.00, CURRENT_TIMESTAMP)
                """);

        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5401L,
                        List.of(
                                new com.fern.platform.contracts.SaleReservationItem(201L, "ING-201", "Sugar", "KG", new BigDecimal("3.0000")),
                                new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("4.0000"))
                        )
                )
        );

        PosSaleCompletedEvent saleEvent = new PosSaleCompletedEvent(
                "sale-event-concurrent-multi-ingredient",
                "pos.sale.completed",
                Instant.parse("2026-03-27T13:15:00Z"),
                "pos-service",
                "corr-sale-concurrent-multi-ingredient",
                "idem-sale-concurrent-multi-ingredient",
                5401L,
                7401L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T13:15:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(
                        new RecipeUsageItem(201L, "ING-201", "Sugar", "KG", new BigDecimal("3.0000")),
                        new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("4.0000"))
                )
        );
        ProcurementGoodsReceiptPostedEvent receiptEvent = goodsReceiptEvent(
                "receipt-event-concurrent-sale-complete",
                "idem-gr-concurrent-sale-complete",
                9308L,
                Instant.parse("2026-03-27T13:20:00Z"),
                List.of(
                        new GoodsReceiptPostedLine(201L, new BigDecimal("6.0000"), new BigDecimal("12000.00"), 3302L),
                        new GoodsReceiptPostedLine(200L, new BigDecimal("5.0000"), new BigDecimal("10000.00"), 3301L)
                )
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> saleCompletion = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeSaleCompleted(saleEvent);
                return null;
            });
            Future<?> receipt = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                inventoryEventConsumerService.consumeGoodsReceiptPosted(receiptEvent);
                return null;
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            saleCompletion.get(10, TimeUnit.SECONDS);
            receipt.get(10, TimeUnit.SECONDS);
        }

        assertThat(balanceField(200L, "qty_on_hand")).isEqualByComparingTo("21.0000");
        assertThat(balanceField(201L, "qty_on_hand")).isEqualByComparingTo("13.0000");
        assertThat(balanceField(200L, "qty_on_hand")).isEqualByComparingTo(new BigDecimal("20.0000").add(ledgerDelta(200L)));
        assertThat(balanceField(201L, "qty_on_hand")).isEqualByComparingTo(new BigDecimal("10.0000").add(ledgerDelta(201L)));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'SALE_USAGE' AND source_reference_id = '5401'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = 'PURCHASE_IN' AND source_reference_id = '9308'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.stock_reservation
                WHERE id = ?
                """, String.class, reservation.reservationId())).isEqualTo("COMMITTED");
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String bearerForOutlets(List<Long> outletIds) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String scopedToken = jwtService.encode(new FernJwtClaims(
                1L,
                "inventory-scoped-user",
                Set.of("outlet_manager"),
                Set.of(
                        PermissionCodes.INVENTORY_BALANCE_READ,
                        PermissionCodes.INVENTORY_LEDGER_READ
                ),
                new ScopeRoots(List.of(1L), outletIds),
                1L,
                1L,
                "inventory-scoped-jti-" + outletIds,
                Instant.now(),
                Instant.now().plusSeconds(900),
                FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("inventory-service")
        ), jwtService.accessTokenTtl());
        return "Bearer " + scopedToken;
    }

    private String concurrentReservationOutcome(
            CountDownLatch start,
            CountDownLatch ready,
            Long sourceOrderId
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            stockReservationService.reserveSale(
                    servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                    new com.fern.platform.contracts.SaleReservationRequest(
                            101L,
                            LocalDate.of(2026, 3, 27),
                            sourceOrderId,
                            List.of(new com.fern.platform.contracts.SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("15.0000")))
                    )
            );
            return "RESERVED";
        } catch (com.fern.platform.common.ConflictException exception) {
            assertThat(exception.getMessage()).isEqualTo("Insufficient available stock for ingredient 200");
            return "CONFLICT";
        }
    }

    private Long concurrentMultiIngredientReservation(
            CountDownLatch start,
            CountDownLatch ready,
            Long sourceOrderId,
            List<com.fern.platform.contracts.SaleReservationItem> usageItems
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new com.fern.platform.contracts.SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        sourceOrderId,
                        usageItems
                )
        ).reservationId();
    }

    private BigDecimal balanceField(Long ingredientId, String field) {
        return jdbcTemplate.queryForObject("""
                SELECT %s
                FROM inventory.stock_balance
                WHERE outlet_id = 101 AND ingredient_id = ?
                """.formatted(field), BigDecimal.class, ingredientId);
    }

    private BigDecimal ledgerDelta(Long ingredientId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(qty_change), 0)
                FROM inventory.inventory_transaction
                WHERE outlet_id = 101 AND ingredient_id = ?
                """, BigDecimal.class, ingredientId);
    }

    private Long createStockAdjustment(String direction, String qty, String reason) throws Exception {
        return createStockAdjustment(200L, direction, qty, reason);
    }

    private Long createStockAdjustment(Long ingredientId, String direction, String qty, String reason) throws Exception {
        String adjustmentResponse = mockMvc.perform(post("/stock-adjustments")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "ingredientId": %d,
                                  "adjustmentDirection": "%s",
                                  "qty": %s,
                                  "businessDate": "2026-03-27",
                                  "reason": "%s"
                                }
                                """.formatted(ingredientId, direction, qty, reason)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(adjustmentResponse);
    }

    private void postStockAdjustment(Long adjustmentId, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/stock-adjustments/{id}/post", adjustmentId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    private Long createWasteRecord(String qty, String reason) throws Exception {
        return createWasteRecord(200L, qty, reason);
    }

    private Long createWasteRecord(Long ingredientId, String qty, String reason) throws Exception {
        String wasteResponse = mockMvc.perform(post("/waste-records")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "ingredientId": %d,
                                  "qty": %s,
                                  "businessDate": "2026-03-27",
                                  "reason": "%s"
                                }
                                """.formatted(ingredientId, qty, reason)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(wasteResponse);
    }

    private void postWasteRecord(Long wasteId, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/waste-records/{id}/post", wasteId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    private Long createStockCountSession() throws Exception {
        return createStockCountSession(List.of(200L));
    }

    private Long createStockCountSession(List<Long> ingredientIds) throws Exception {
        String sessionResponse = mockMvc.perform(post("/stock-count-sessions")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "countDate": "2026-03-27",
                                  "ingredientIds": [%s]
                                }
                                """.formatted(ingredientIds.stream()
                                .map(String::valueOf)
                                .reduce((left, right) -> left + ", " + right)
                                .orElse(""))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(sessionResponse);
    }

    private void startStockCountSession(Long sessionId) throws Exception {
        mockMvc.perform(post("/stock-count-sessions/{id}/start", sessionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTING"));
    }

    private void updateStockCountLines(Long sessionId, String actualQty) throws Exception {
        mockMvc.perform(put("/stock-count-sessions/{id}/lines", sessionId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "lines": [
                                    {"ingredientId": 200, "actualQty": %s}
                                  ]
                                }
                                """.formatted(actualQty)))
                .andExpect(status().isOk());
    }

    private void postStockCountSession(Long sessionId, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/stock-count-sessions/{id}/post", sessionId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    private String outboxPayloadField(String aggregateType, String aggregateId, String eventType, String field) {
        return jdbcTemplate.queryForObject("""
                SELECT payload ->> '%s'
                FROM inventory.outbox_event
                WHERE aggregate_type = '%s'
                  AND aggregate_id = '%s'
                  AND event_type = '%s'
                ORDER BY created_at, id
                LIMIT 1
                """.formatted(field, aggregateType, aggregateId, eventType), String.class);
    }

    private int concurrentInventoryPostStatus(
            CountDownLatch start,
            CountDownLatch ready,
            String path,
            String idempotencyKey
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", idempotencyKey))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private ProcurementGoodsReceiptPostedEvent goodsReceiptEvent(
            String eventId,
            String idempotencyKey,
            Long goodsReceiptId,
            BigDecimal qtyReceived,
            Instant occurredAt
    ) {
        return goodsReceiptEvent(
                eventId,
                idempotencyKey,
                goodsReceiptId,
                occurredAt,
                List.of(new GoodsReceiptPostedLine(200L, qtyReceived, new BigDecimal("10000.00"), 3301L))
        );
    }

    private ProcurementGoodsReceiptPostedEvent goodsReceiptEvent(
            String eventId,
            String idempotencyKey,
            Long goodsReceiptId,
            Instant occurredAt,
            List<GoodsReceiptPostedLine> lines
    ) {
        return new ProcurementGoodsReceiptPostedEvent(
                eventId,
                "procurement.goods_receipt.posted",
                occurredAt,
                "procurement-service",
                "corr-" + eventId,
                idempotencyKey,
                goodsReceiptId,
                8301L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                occurredAt,
                2L,
                lines
        );
    }

    private Long readId(String json) throws Exception {
        return objectMapper.readTree(json).get("id").asLong();
    }

    private FernPrincipal servicePrincipal(Set<String> permissions) {
        return new FernPrincipal(
                1L,
                "inventory-service",
                Set.of("inventory-service"),
                permissions,
                new ScopeRoots(List.of(1L), List.of(101L)),
                1L,
                1L,
                "inventory-service-jti-" + permissions.hashCode(),
                FernPrincipalType.SERVICE
        );
    }

    private String serviceBearer(Set<String> permissions) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        Instant now = Instant.now();
        String serviceToken = jwtService.encode(new FernJwtClaims(
                null,
                "org-service",
                Set.of(),
                permissions,
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "inventory-service-test-jti-" + permissions.hashCode(),
                now,
                now.plus(jwtService.serviceTokenTtl()),
                FernPrincipalType.SERVICE,
                "org-service",
                Set.of("inventory-service")
        ), jwtService.serviceTokenTtl());
        return "Bearer " + serviceToken;
    }

    private static void ensureOrgServerStarted() {
        if (orgServer != null) {
            return;
        }
        try {
            startOrgServer();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start inventory org stub", exception);
        }
    }
}
