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
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
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
class ProcurementServiceIntegrationTest {
    private static HttpServer orgServer;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensureOrgServerStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.clients.org.base-url", () -> "http://localhost:" + orgServer.getAddress().getPort());
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
        properties.setAllowInsecureDefaultSecret(true);
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
        Long supplierId = createActiveSupplier("SUP-001", "Acme Supplier");

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

    @Test
    void shouldRejectPurchaseOrderWhenRegionDoesNotMatchOutletRoute() throws Exception {
        Long supplierId = createActiveSupplier("SUP-002", "Mismatch Supplier");

        mockMvc.perform(post("/purchase-orders")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionId": 999,
                                  "outletId": 101,
                                  "supplierId": %d,
                                  "orderDate": "2026-03-27",
                                  "expectedDeliveryDate": "2026-03-29",
                                  "lines": [
                                    {
                                      "ingredientId": 200,
                                      "uomCode": "KG",
                                      "qtyOrdered": 5.0000
                                    }
                                  ]
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUpdatingSubmittedPurchaseOrder() throws Exception {
        Long supplierId = createActiveSupplier("SUP-003", "Workflow Supplier");
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
                                      "qtyOrdered": 5.0000
                                    }
                                  ]
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long purchaseOrderId = readId(poJson);

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(patch("/purchase-orders/{id}", purchaseOrderId)
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedDeliveryDate": "2026-03-30",
                                  "lines": [
                                    {
                                      "ingredientId": 200,
                                      "uomCode": "KG",
                                      "qtyOrdered": 9.0000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectPurchaseOrderCreationForEmptyScopePrincipal() throws Exception {
        Long supplierId = createActiveSupplier("SUP-004", "Scoped Supplier");
        String emptyScopeToken = issueToken(Set.of("procurement.po.create"), List.of(), List.of());

        mockMvc.perform(post("/purchase-orders")
                        .header("Authorization", "Bearer " + emptyScopeToken)
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
                                      "qtyOrdered": 5.0000
                                    }
                                  ]
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectSupplierInvoiceWhenOutletRouteDoesNotMatchRegion() throws Exception {
        Long supplierId = createActiveSupplier("SUP-004B", "Invoice Route Supplier");
        String wrongRegionToken = issueToken(Set.of("procurement.invoice.review"), List.of(999L), List.of());

        mockMvc.perform(post("/supplier-invoices")
                        .header("Authorization", "Bearer " + wrongRegionToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierId": %d,
                                  "regionId": 999,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "invoiceNumber": "INV-ROUTE-1",
                                  "invoiceDate": "2026-03-27",
                                  "lines": [
                                    {
                                      "lineType": "MANUAL",
                                      "description": "Route check",
                                      "lineTotal": 100.00
                                    }
                                  ]
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectSupplierPaymentForInvoiceOutsidePrincipalScope() throws Exception {
        Long supplierId = createActiveSupplier("SUP-005", "Invoice Scope Supplier");

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
                .andReturn().getResponse().getContentAsString();
        Long purchaseOrderId = readId(poJson);

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/purchase-orders/{id}/approve", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        String issuedPoJson = mockMvc.perform(post("/purchase-orders/{id}/issue", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
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
                .andExpect(status().isOk());
        String postedReceiptJson = mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-scope-check"))
                .andExpect(status().isOk())
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
                                  "invoiceNumber": "INV-005",
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
                .andExpect(status().isOk());

        String foreignScopeToken = issueToken(Set.of("procurement.payment.record"), List.of(999L), List.of(999L));

        mockMvc.perform(post("/supplier-payments")
                        .header("Authorization", "Bearer " + foreignScopeToken)
                        .header("Idempotency-Key", "supplier-pay-scope-deny")
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
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPostingGoodsReceiptTwiceWithDifferentIdempotencyKeys() throws Exception {
        Long supplierId = createActiveSupplier("SUP-006", "Replay Supplier");

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
                .andReturn().getResponse().getContentAsString();
        Long purchaseOrderId = readId(poJson);

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/purchase-orders/{id}/approve", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        String issuedPoJson = mockMvc.perform(post("/purchase-orders/{id}/issue", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
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
                .andExpect(status().isOk());

        mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-replay-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-replay-2"))
                .andExpect(status().isConflict());

        Integer postedEventCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE event_type = 'procurement.goods_receipt.posted'
                """, Integer.class);
        assertThat(postedEventCount).isEqualTo(1);
    }

    private Long createActiveSupplier(String supplierCode, String name) throws Exception {
        String supplierJson = mockMvc.perform(post("/suppliers")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierCode": "%s",
                                  "name": "%s",
                                  "status": "INACTIVE"
                                }
                                """.formatted(supplierCode, name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long supplierId = readId(supplierJson);

        mockMvc.perform(post("/suppliers/{id}/activate", supplierId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        return supplierId;
    }

    private static void ensureOrgServerStarted() {
        if (orgServer != null) {
            return;
        }
        try {
            startOrgServer();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start procurement org stub", exception);
        }
    }

    private Long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String issueToken(Set<String> permissions, List<Long> regions, List<Long> outlets) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        return jwtService.encode(new FernJwtClaims(
                1L,
                "procurement-tester",
                Set.of("regional_finance", "outlet_manager"),
                permissions,
                new ScopeRoots(regions, outlets),
                1L,
                1L,
                "procurement-test-jti-" + permissions.hashCode() + "-" + regions.hashCode() + "-" + outlets.hashCode(),
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
    }
}
