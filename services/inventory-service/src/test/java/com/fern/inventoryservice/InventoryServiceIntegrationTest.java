package com.fern.inventoryservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.inventoryservice.service.InventoryService;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("inventory"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryService inventoryService;

    private String token;

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
    void shouldConsumeSaleAndGoodsReceiptEventsIdempotently() {
        inventoryService.reserveSale(new com.fern.platform.common.FernPrincipal(
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
        inventoryService.consumeSaleCompleted(saleEvent);
        inventoryService.consumeSaleCompleted(saleEvent);

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
        inventoryService.consumeGoodsReceiptPosted(receiptEvent);
        inventoryService.consumeGoodsReceiptPosted(receiptEvent);

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
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
