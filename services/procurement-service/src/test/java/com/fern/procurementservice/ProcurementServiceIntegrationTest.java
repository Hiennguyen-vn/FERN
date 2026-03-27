package com.fern.procurementservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.Clock;
import java.time.Instant;
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
class ProcurementServiceIntegrationTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.outbox.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    procurement.supplier_payment_allocation,
                    procurement.supplier_payment,
                    procurement.supplier_invoice_line,
                    procurement.supplier_invoice,
                    procurement.goods_receipt_line,
                    procurement.goods_receipt,
                    procurement.purchase_order_line,
                    procurement.purchase_order,
                    procurement.outbox_event,
                    procurement_master.supplier_region_coverage,
                    procurement_master.supplier_contact,
                    procurement_master.supplier
                RESTART IDENTITY CASCADE
                """);
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        token = jwtService.encode(new FernJwtClaims(
                1L,
                "procurement-tester",
                Set.of("finance", "regional_finance", "outlet_manager"),
                Set.of(
                        "procurement.supplier.read",
                        "procurement.supplier.write",
                        "procurement.po.read",
                        "procurement.po.create",
                        "procurement.po.update",
                        "procurement.po.submit",
                        "procurement.po.approve",
                        "procurement.po.issue",
                        "procurement.po.cancel",
                        "procurement.gr.read",
                        "procurement.gr.create",
                        "procurement.gr.post",
                        "procurement.gr.cancel",
                        "procurement.invoice.read",
                        "procurement.invoice.review",
                        "procurement.invoice.approve",
                        "procurement.invoice.dispute",
                        "procurement.payment.read",
                        "procurement.payment.record"
                ),
                new ScopeRoots(java.util.List.of(1L), java.util.List.of(101L)),
                1L,
                1L,
                "procurement-test-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
    }

    @Test
    void shouldRunSupplierPoGrInvoicePaymentFlow() throws Exception {
        String supplierJson = mockMvc.perform(post("/suppliers")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierCode": "SUP-001",
                                  "name": "Acme Supplier",
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long supplierId = readId(supplierJson);

        mockMvc.perform(post("/suppliers/{id}/activate", supplierId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String poJson = mockMvc.perform(post("/purchase-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 1,
                                  "outletId": 101,
                                  "supplierId": %d,
                                  "orderDate": "2026-03-27",
                                  "expectedDeliveryDate": "2026-03-29",
                                  "lines": [
                                    {
                                      "ingredientId": 200,
                                      "uomCode": "KG",
                                      "qtyOrdered": 5.0000,
                                      "expectedUnitPrice": 12.50,
                                      "taxPercent": 10.00
                                    }
                                  ]
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Long purchaseOrderId = readId(poJson);

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/purchase-orders/{id}/approve", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String issuedPoJson = mockMvc.perform(post("/purchase-orders/{id}/issue", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andReturn().getResponse().getContentAsString();
        Long poLineId = objectMapper.readTree(issuedPoJson).get("lines").get(0).get("id").asLong();

        String receiptJson = mockMvc.perform(post("/goods-receipts")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "purchaseOrderId": %d,
                                  "receiptTime": "2026-03-27T10:00:00Z",
                                  "businessDate": "2026-03-27",
                                  "lines": [
                                    {
                                      "purchaseOrderLineId": %d,
                                      "ingredientId": 200,
                                      "uomCode": "KG",
                                      "qtyReceived": 3.0000,
                                      "unitCost": 12.50
                                    }
                                  ]
                                }
                                """.formatted(purchaseOrderId, poLineId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long goodsReceiptId = readId(receiptJson);

        mockMvc.perform(post("/goods-receipts/{id}/receive", goodsReceiptId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        String postedReceiptJson = mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andReturn().getResponse().getContentAsString();
        Long goodsReceiptLineId = objectMapper.readTree(postedReceiptJson).get("lines").get(0).get("id").asLong();

        String invoiceJson = mockMvc.perform(post("/supplier-invoices")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierId": %d,
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "invoiceNumber": "INV-001",
                                  "invoiceDate": "2026-03-27",
                                  "lines": [
                                    {
                                      "lineType": "STOCK",
                                      "goodsReceiptLineId": %d,
                                      "description": "Milk delivery",
                                      "qtyInvoiced": 3.0000,
                                      "unitPrice": 12.50,
                                      "taxPercent": 10.00,
                                      "taxAmount": 3.75,
                                      "lineTotal": 41.25
                                    }
                                  ]
                                }
                                """.formatted(supplierId, goodsReceiptLineId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long supplierInvoiceId = readId(invoiceJson);

        mockMvc.perform(post("/supplier-invoices/{id}/approve", supplierInvoiceId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/supplier-payments")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "supplier-pay-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierId": %d,
                                  "currencyCode": "VND",
                                  "paymentMethod": "BANK_TRANSFER",
                                  "amount": 41.25,
                                  "paymentTime": "2026-03-27T12:00:00Z",
                                  "invoiceAllocations": [
                                    {
                                      "supplierInvoiceId": %d,
                                      "allocatedAmount": 41.25
                                    }
                                  ]
                                }
                                """.formatted(supplierId, supplierInvoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceAllocations[0].supplierInvoiceId").value(supplierInvoiceId));

        String poStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.purchase_order
                WHERE id = ?
                """, String.class, purchaseOrderId);
        assertThat(poStatus).isEqualTo("PARTIALLY_RECEIVED");

        java.util.List<String> eventTypes = jdbcTemplate.queryForList("""
                SELECT event_type
                FROM procurement.outbox_event
                ORDER BY created_at
                """, String.class);
        assertThat(eventTypes).contains("procurement.goods_receipt.posted", "procurement.supplier.payment.recorded");
    }

    private Long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
