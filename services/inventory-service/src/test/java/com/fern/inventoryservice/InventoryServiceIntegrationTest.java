package com.fern.inventoryservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.inventoryservice.service.InventoryEventConsumerService;
import com.fern.inventoryservice.service.StockReservationService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
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
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.inventory.reservation-ttl", () -> "60s");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                Instant.now().plusSeconds(900)
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

    private String bearer() {
        return "Bearer " + token;
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
