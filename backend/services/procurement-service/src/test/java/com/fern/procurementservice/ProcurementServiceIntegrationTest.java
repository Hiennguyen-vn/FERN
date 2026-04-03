package com.fern.procurementservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
    private static volatile String lastOrgAuthorization;

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
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
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
            lastOrgAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
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
        lastOrgAuthorization = null;
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
                Instant.now().plusSeconds(900),
                com.fern.platform.common.FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("procurement-service")
        ), jwtService.accessTokenTtl());
    }

    @Test
    void shouldRejectSupplierWithInvalidStatus() throws Exception {
        mockMvc.perform(post("/suppliers")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierCode": "SUP-BAD-STATUS",
                                  "name": "Invalid Supplier",
                                  "status": "ARCHIVED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.status").value("must be one of ACTIVE, INACTIVE, or SUSPENDED"));
    }

    @Test
    void shouldRejectSupplierInvoiceWithInvalidLineType() throws Exception {
        mockMvc.perform(post("/supplier-invoices")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierId": 1,
                                  "regionId": 1,
                                  "outletId": 101,
                                  "currencyCode": "VND",
                                  "invoiceNumber": "INV-BAD-LINE-TYPE",
                                  "invoiceDate": "2026-03-27",
                                  "lines": [
                                    {
                                      "lineType": "BROKEN",
                                      "lineTotal": 10.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details['lines[0].lineType']").value(
                        "must be one of STOCK, PARTIAL_MATCH, NON_PO_RECEIPT, or NON_STOCK"
                ));
    }

    @Test
    void shouldRejectSupplierPaymentWithInvalidPaymentMethod() throws Exception {
        mockMvc.perform(post("/supplier-payments")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "supplier-pay-invalid-method")
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierId": 1,
                                  "currencyCode": "VND",
                                  "paymentMethod": "WIRE",
                                  "amount": 30.00,
                                  "paymentTime": "2026-03-27T12:00:00Z",
                                  "invoiceAllocations": [
                                    {
                                      "supplierInvoiceId": 1,
                                      "allocatedAmount": 30.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.paymentMethod").value(
                        "must be one of CASH, CARD, EWALLET, BANK_TRANSFER, CHEQUE, or VOUCHER"
                ));
    }

    @Test
    void shouldListSupplierPayments() throws Exception {
        Long supplierId = createActiveSupplier("SUP-LIST", "List Payments Supplier");

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

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId).header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/purchase-orders/{id}/approve", purchaseOrderId).header("Authorization", bearer()))
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

        mockMvc.perform(post("/goods-receipts/{id}/receive", goodsReceiptId).header("Authorization", bearer()))
                .andExpect(status().isOk());
        String postedReceiptJson = mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-list-payments"))
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
                                  "invoiceNumber": "INV-LIST-001",
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

        mockMvc.perform(post("/supplier-payments")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "supplier-pay-list-001")
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
                .andExpect(status().isOk());

        mockMvc.perform(get("/supplier-payments")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supplierId").value(supplierId))
                .andExpect(jsonPath("$[0].invoiceAllocations[0].supplierInvoiceId").value(supplierInvoiceId));
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
        Long supplierPaymentId = jdbcTemplate.queryForObject("""
                SELECT MAX(id)
                FROM procurement.supplier_payment
                """, Long.class);
        assertThat(new BigDecimal(objectMapper.readTree(postedReceiptJson).get("postedAt").asText()))
                .isEqualByComparingTo(new BigDecimal(outboxPayloadField(
                        "GOODS_RECEIPT",
                        goodsReceiptId.toString(),
                        "procurement.goods_receipt.posted",
                        "postedAt"
                )));
        assertThat(outboxPayloadField("GOODS_RECEIPT", goodsReceiptId.toString(), "procurement.goods_receipt.posted", "idempotencyKey"))
                .isEqualTo("procurement.goods_receipt.posted:receipt:" + goodsReceiptId);
        assertThat(outboxPayloadField("SUPPLIER_PAYMENT", supplierPaymentId.toString(), "procurement.supplier.payment.recorded", "idempotencyKey"))
                .isEqualTo("procurement.supplier.payment.recorded:payment:" + supplierPaymentId);
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Region does not match the outlet route"));
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
                                      "lineType": "NON_STOCK",
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

    @Test
    void shouldRejectCreatingGoodsReceiptFromApprovedPurchaseOrder() throws Exception {
        Long supplierId = createActiveSupplier("SUP-007", "Approved Only Supplier");

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
        Long poLineId = objectMapper.readTree(poJson).get("lines").get(0).get("id").asLong();

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/purchase-orders/{id}/approve", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/goods-receipts")
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Goods receipts can only be created from ordered or partially received purchase orders"));
    }

    @Test
    void shouldEnforceGoodsReceiptLifecycleTransitions() throws Exception {
        IssuedPurchaseOrderFixture fixture = createIssuedPurchaseOrderFixture(
                "SUP-GR-LIFECYCLE",
                "Lifecycle Supplier",
                "5.0000"
        );
        Long goodsReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "3.0000",
                "2026-03-27T10:00:00Z"
        );

        mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-before-receive"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only received goods receipts can be posted"));

        receiveGoodsReceipt(goodsReceiptId);
        postGoodsReceipt(goodsReceiptId, "gr-post-after-receive");

        mockMvc.perform(post("/goods-receipts/{id}/receive", goodsReceiptId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only draft goods receipts can be received"));

        mockMvc.perform(post("/goods-receipts/{id}/cancel", goodsReceiptId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only draft or received goods receipts can be cancelled"));
    }

    @Test
    void shouldRejectGoodsReceiptLineWhenIngredientDoesNotMatchPurchaseOrderLine() throws Exception {
        IssuedPurchaseOrderFixture fixture = createIssuedPurchaseOrderFixture(
                "SUP-GR-MISMATCH",
                "Mismatch Supplier",
                "5.0000"
        );

        mockMvc.perform(post("/goods-receipts")
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
                                      "ingredientId": 201,
                                      "uomCode": "KG",
                                      "qtyReceived": 3.0000,
                                      "unitCost": 12.50
                                    }
                                  ]
                                }
                                """.formatted(fixture.purchaseOrderId(), fixture.purchaseOrderLineId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Goods receipt line ingredient does not match purchase order line 1"));
    }

    @Test
    void shouldRejectPostingGoodsReceiptThatWouldOverReceivePurchaseOrderLine() throws Exception {
        IssuedPurchaseOrderFixture fixture = createIssuedPurchaseOrderFixture(
                "SUP-GR-OVER",
                "Over Receipt Supplier",
                "5.0000"
        );

        Long firstReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "4.0000",
                "2026-03-27T09:00:00Z"
        );
        receiveGoodsReceipt(firstReceiptId);
        postGoodsReceipt(firstReceiptId, "gr-over-batch-1");

        Long secondReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "2.0000",
                "2026-03-27T11:00:00Z"
        );
        receiveGoodsReceipt(secondReceiptId);

        mockMvc.perform(post("/goods-receipts/{id}/post", secondReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-over-batch-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Goods receipt would over-receive purchase order line 1"));

        BigDecimal qtyReceived = jdbcTemplate.queryForObject("""
                SELECT qty_received
                FROM procurement.purchase_order_line
                WHERE id = ?
                """, BigDecimal.class, fixture.purchaseOrderLineId());
        String headerStatus = jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.purchase_order
                WHERE id = ?
                """, String.class, fixture.purchaseOrderId());

        assertThat(qtyReceived).isEqualByComparingTo("4.0000");
        assertThat(headerStatus).isEqualTo("PARTIALLY_RECEIVED");
    }

    @Test
    void shouldTrackPartialGoodsReceiptsAcrossMultipleBatchesForSamePurchaseOrder() throws Exception {
        IssuedPurchaseOrderFixture fixture = createIssuedPurchaseOrderFixture(
                "SUP-013",
                "Partial Receipt Supplier",
                "5.0000"
        );

        Long firstReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "2.0000",
                "2026-03-27T09:00:00Z"
        );
        receiveGoodsReceipt(firstReceiptId);
        postGoodsReceipt(firstReceiptId, "gr-partial-batch-1");

        BigDecimal qtyReceivedAfterFirst = jdbcTemplate.queryForObject("""
                SELECT qty_received
                FROM procurement.purchase_order_line
                WHERE id = ?
                """, BigDecimal.class, fixture.purchaseOrderLineId());
        String lineStatusAfterFirst = jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.purchase_order_line
                WHERE id = ?
                """, String.class, fixture.purchaseOrderLineId());
        String headerStatusAfterFirst = jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.purchase_order
                WHERE id = ?
                """, String.class, fixture.purchaseOrderId());

        assertThat(qtyReceivedAfterFirst).isEqualByComparingTo("2.0000");
        assertThat(lineStatusAfterFirst).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(headerStatusAfterFirst).isEqualTo("PARTIALLY_RECEIVED");

        Long secondReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "3.0000",
                "2026-03-27T11:00:00Z"
        );
        receiveGoodsReceipt(secondReceiptId);
        postGoodsReceipt(secondReceiptId, "gr-partial-batch-2");

        BigDecimal qtyReceivedAfterSecond = jdbcTemplate.queryForObject("""
                SELECT qty_received
                FROM procurement.purchase_order_line
                WHERE id = ?
                """, BigDecimal.class, fixture.purchaseOrderLineId());
        String lineStatusAfterSecond = jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.purchase_order_line
                WHERE id = ?
                """, String.class, fixture.purchaseOrderLineId());
        String headerStatusAfterSecond = jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.purchase_order
                WHERE id = ?
                """, String.class, fixture.purchaseOrderId());
        Integer postedEventCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE event_type = 'procurement.goods_receipt.posted'
                """, Integer.class);

        assertThat(qtyReceivedAfterSecond).isEqualByComparingTo("5.0000");
        assertThat(lineStatusAfterSecond).isEqualTo("COMPLETED");
        assertThat(headerStatusAfterSecond).isEqualTo("COMPLETED");
        assertThat(postedEventCount).isEqualTo(2);
    }

    @Test
    void shouldReturnSameGoodsReceiptWhenPostRequestIsRetriedWithSameIdempotencyKey() throws Exception {
        IssuedPurchaseOrderFixture fixture = createIssuedPurchaseOrderFixture(
                "SUP-014",
                "Goods Receipt Retry Supplier",
                "5.0000"
        );

        Long goodsReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "3.0000",
                "2026-03-27T10:00:00Z"
        );
        receiveGoodsReceipt(goodsReceiptId);

        String firstPostBody = postGoodsReceipt(goodsReceiptId, "gr-post-retry-same-key");
        String secondPostBody = postGoodsReceipt(goodsReceiptId, "gr-post-retry-same-key");

        JsonNode firstPost = objectMapper.readTree(firstPostBody);
        JsonNode secondPost = objectMapper.readTree(secondPostBody);
        Integer postedEventCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE event_type = 'procurement.goods_receipt.posted'
                """, Integer.class);
        BigDecimal qtyReceived = jdbcTemplate.queryForObject("""
                SELECT qty_received
                FROM procurement.purchase_order_line
                WHERE id = ?
                """, BigDecimal.class, fixture.purchaseOrderLineId());

        assertThat(secondPost.get("id").asLong()).isEqualTo(firstPost.get("id").asLong());
        assertThat(secondPost.get("status").asText()).isEqualTo("POSTED");
        assertThat(secondPost.get("postedAt").asText()).isEqualTo(firstPost.get("postedAt").asText());
        assertThat(postedEventCount).isEqualTo(1);
        assertThat(qtyReceived).isEqualByComparingTo("3.0000");
    }

    @Test
    void shouldRejectPostGoodsReceiptReplayWhenIdempotencyKeyTargetsDifferentReceipt() throws Exception {
        IssuedPurchaseOrderFixture fixture = createIssuedPurchaseOrderFixture(
                "SUP-014B",
                "Goods Receipt Retry Mismatch Supplier",
                "5.0000"
        );

        Long firstReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "2.0000",
                "2026-03-27T10:00:00Z"
        );
        Long secondReceiptId = createGoodsReceipt(
                fixture.purchaseOrderId(),
                fixture.purchaseOrderLineId(),
                "1.0000",
                "2026-03-27T11:00:00Z"
        );
        receiveGoodsReceipt(firstReceiptId);
        receiveGoodsReceipt(secondReceiptId);

        mockMvc.perform(post("/goods-receipts/{id}/post", firstReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-retry-mismatch-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstReceiptId));

        mockMvc.perform(post("/goods-receipts/{id}/post", secondReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "gr-post-retry-mismatch-key"))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.goods_receipt
                WHERE id = ?
                """, String.class, secondReceiptId)).isEqualTo("RECEIVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.outbox_event
                WHERE event_type = 'procurement.goods_receipt.posted'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldSerializeConcurrentSupplierPaymentsAgainstSameInvoice() throws Exception {
        ApprovedSupplierInvoiceFixture fixture = createApprovedSupplierInvoiceFixture(
                "SUP-009",
                "Concurrent Payment Supplier",
                "INV-009",
                "gr-post-concurrency"
        );
        Long supplierId = fixture.supplierId();
        Long supplierInvoiceId = fixture.supplierInvoiceId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> concurrentPaymentStatus(start, ready, supplierId, supplierInvoiceId, "30.00", "supplier-pay-race-1"));
            Future<Integer> second = executor.submit(() -> concurrentPaymentStatus(start, ready, supplierId, supplierInvoiceId, "30.00", "supplier-pay-race-2"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        Integer paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.supplier_payment
                WHERE supplier_id = ?
                """, Integer.class, supplierId);
        BigDecimal allocatedTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocated_amount), 0)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = ?
                """, BigDecimal.class, supplierInvoiceId);

        assertThat(paymentCount).isEqualTo(1);
        assertThat(allocatedTotal).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldSerializeThreeConcurrentSupplierPaymentsWithoutOverAllocatingInvoice() throws Exception {
        ApprovedSupplierInvoiceFixture fixture = createApprovedSupplierInvoiceFixture(
                "SUP-010",
                "Concurrent Triple Payment Supplier",
                "INV-010",
                "gr-post-concurrency-3"
        );
        Long supplierId = fixture.supplierId();
        Long supplierInvoiceId = fixture.supplierInvoiceId();

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> concurrentPaymentStatus(start, ready, supplierId, supplierInvoiceId, "15.00", "supplier-pay-race-3a"));
            Future<Integer> second = executor.submit(() -> concurrentPaymentStatus(start, ready, supplierId, supplierInvoiceId, "15.00", "supplier-pay-race-3b"));
            Future<Integer> third = executor.submit(() -> concurrentPaymentStatus(start, ready, supplierId, supplierInvoiceId, "15.00", "supplier-pay-race-3c"));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            int thirdStatus = third.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstStatus, secondStatus, thirdStatus)).containsExactlyInAnyOrder(200, 200, 409);
        } finally {
            executor.shutdownNow();
        }

        Integer paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.supplier_payment
                WHERE supplier_id = ?
                """, Integer.class, supplierId);
        BigDecimal allocatedTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocated_amount), 0)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = ?
                """, BigDecimal.class, supplierInvoiceId);

        assertThat(paymentCount).isEqualTo(2);
        assertThat(allocatedTotal).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldReturnSameSupplierPaymentForConcurrentRequestsSharingIdempotencyKey() throws Exception {
        ApprovedSupplierInvoiceFixture fixture = createApprovedSupplierInvoiceFixture(
                "SUP-011",
                "Concurrent Idempotent Payment Supplier",
                "INV-011",
                "gr-post-concurrency-idempotent"
        );
        Long supplierId = fixture.supplierId();
        Long supplierInvoiceId = fixture.supplierInvoiceId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PaymentCallResult> first = executor.submit(
                    () -> concurrentPaymentResult(start, ready, supplierId, supplierInvoiceId, "30.00", "supplier-pay-race-same-key")
            );
            Future<PaymentCallResult> second = executor.submit(
                    () -> concurrentPaymentResult(start, ready, supplierId, supplierInvoiceId, "30.00", "supplier-pay-race-same-key")
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            PaymentCallResult firstResult = first.get(10, TimeUnit.SECONDS);
            PaymentCallResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.status()).isEqualTo(200);
            assertThat(secondResult.status()).isEqualTo(200);
            assertThat(objectMapper.readTree(firstResult.body()).get("id").asLong())
                    .isEqualTo(objectMapper.readTree(secondResult.body()).get("id").asLong());
        } finally {
            executor.shutdownNow();
        }

        Integer paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.supplier_payment
                WHERE supplier_id = ?
                """, Integer.class, supplierId);
        Integer allocationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = ?
                """, Integer.class, supplierInvoiceId);
        BigDecimal allocatedTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocated_amount), 0)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = ?
                """, BigDecimal.class, supplierInvoiceId);

        assertThat(paymentCount).isEqualTo(1);
        assertThat(allocationCount).isEqualTo(1);
        assertThat(allocatedTotal).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldRejectConcurrentSupplierPaymentReplayWhenIdempotencyPayloadDiffers() throws Exception {
        ApprovedSupplierInvoiceFixture fixture = createApprovedSupplierInvoiceFixture(
                "SUP-012",
                "Concurrent Idempotency Mismatch Supplier",
                "INV-012",
                "gr-post-concurrency-idempotent-mismatch"
        );
        Long supplierId = fixture.supplierId();
        Long supplierInvoiceId = fixture.supplierInvoiceId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PaymentCallResult> first = executor.submit(
                    () -> concurrentPaymentResult(start, ready, supplierId, supplierInvoiceId, "30.00", "30.00", "supplier-pay-race-mismatch-key")
            );
            Future<PaymentCallResult> second = executor.submit(
                    () -> concurrentPaymentResult(start, ready, supplierId, supplierInvoiceId, "20.00", "20.00", "supplier-pay-race-mismatch-key")
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            PaymentCallResult firstResult = first.get(10, TimeUnit.SECONDS);
            PaymentCallResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(List.of(firstResult.status(), secondResult.status())).containsExactlyInAnyOrder(200, 409);
            PaymentCallResult conflictResult = firstResult.status() == 409 ? firstResult : secondResult;
            assertThat(conflictResult.body()).contains("Idempotency-Key cannot be reused with a different supplier payment request");
        } finally {
            executor.shutdownNow();
        }

        Integer paymentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.supplier_payment
                WHERE supplier_id = ?
                """, Integer.class, supplierId);
        Integer allocationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = ?
                """, Integer.class, supplierInvoiceId);
        BigDecimal allocatedTotal = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocated_amount), 0)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = ?
                """, BigDecimal.class, supplierInvoiceId);

        assertThat(paymentCount).isEqualTo(1);
        assertThat(allocationCount).isEqualTo(1);
        assertThat(allocatedTotal).isIn(new BigDecimal("20.00"), new BigDecimal("30.00"));
    }

    @Test
    void shouldRejectDisputingSupplierInvoiceAfterPaymentAllocationExists() throws Exception {
        ApprovedSupplierInvoiceFixture fixture = createApprovedSupplierInvoiceFixture(
                "SUP-013",
                "Allocated Invoice Supplier",
                "INV-013",
                "gr-post-dispute-after-payment"
        );

        mockMvc.perform(post("/supplier-payments")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "supplier-pay-dispute-lock")
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
                                """.formatted(fixture.supplierId(), fixture.supplierInvoiceId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/supplier-invoices/{id}/dispute", fixture.supplierInvoiceId())
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Supplier invoices with recorded payments cannot be disputed"));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM procurement.supplier_invoice
                WHERE id = ?
                """, String.class, fixture.supplierInvoiceId())).isEqualTo("APPROVED");
    }

    @Test
    @Tag("security-gap")
    void shouldRejectReadingProcurementObjectsOutsideOutletScopeById() throws Exception {
        Long supplierId = createActiveSupplier("SUP-GAP-001", "Scoped Procurement Supplier");

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
        Long poLineId = objectMapper.readTree(poJson).get("lines").get(0).get("id").asLong();

        mockMvc.perform(post("/purchase-orders/{id}/submit", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/purchase-orders/{id}/approve", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/purchase-orders/{id}/issue", purchaseOrderId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

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
                        .header("Idempotency-Key", "gr-post-gap-001"))
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
                                  "invoiceNumber": "INV-GAP-001",
                                  "invoiceDate": "2026-03-27",
                                  "lines": [
                                    {
                                      "lineType": "STOCK",
                                      "goodsReceiptLineId": %d,
                                      "description": "Scoped milk delivery",
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

        String inScopeBearer = "Bearer " + issueToken(
                Set.of("procurement.po.read", "procurement.gr.read", "procurement.invoice.read"),
                List.of(),
                List.of(101L)
        );
        String outOfScopeBearer = "Bearer " + issueToken(
                Set.of("procurement.po.read", "procurement.gr.read", "procurement.invoice.read"),
                List.of(),
                List.of(999L)
        );

        mockMvc.perform(get("/purchase-orders/{id}", purchaseOrderId).header("Authorization", inScopeBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(purchaseOrderId));
        mockMvc.perform(get("/purchase-orders/{id}", purchaseOrderId).header("Authorization", outOfScopeBearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/goods-receipts/{id}", goodsReceiptId).header("Authorization", inScopeBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(goodsReceiptId));
        mockMvc.perform(get("/goods-receipts/{id}", goodsReceiptId).header("Authorization", outOfScopeBearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/supplier-invoices/{id}", supplierInvoiceId).header("Authorization", inScopeBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplierInvoiceId));
        mockMvc.perform(get("/supplier-invoices/{id}", supplierInvoiceId).header("Authorization", outOfScopeBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("security-gap")
    void shouldIssueProcurementServiceTokenForOrgAudience() throws Exception {
        Long supplierId = createActiveSupplier("SUP-GAP-ORG", "Org Audience Supplier");

        mockMvc.perform(post("/purchase-orders")
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
                                      "qtyOrdered": 1.0000,
                                      "expectedUnitPrice": 12.50,
                                      "taxPercent": 10.00
                                    }
                                  ]
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isOk());

        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        FernJwtClaims claims = jwtService.decode(lastOrgAuthorization.substring("Bearer ".length()));

        assertThat(claims.issuer()).isEqualTo("procurement-service");
        assertThat(claims.audience()).containsExactly("org-service");
    }

    @Test
    void shouldReturnBlockingSummaryForOutletCloseCheck() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO procurement.purchase_order (
                    po_number, region_id, outlet_id, supplier_id, order_date, status,
                    subtotal_amount, tax_amount, total_amount, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "PO-CLOSE-CHECK-1", 1L, 101L, 1L, java.sql.Date.valueOf("2026-03-27"), "ORDERED",
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("110.00"));
        jdbcTemplate.update("""
                INSERT INTO procurement.purchase_order (
                    po_number, region_id, outlet_id, supplier_id, order_date, status,
                    subtotal_amount, tax_amount, total_amount, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "PO-CLOSE-CHECK-CANCELLED", 1L, 101L, 1L, java.sql.Date.valueOf("2026-03-27"), "CANCELLED",
                new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"));
        jdbcTemplate.update("""
                INSERT INTO procurement.purchase_order (
                    po_number, region_id, outlet_id, supplier_id, order_date, status,
                    subtotal_amount, tax_amount, total_amount, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "PO-CLOSE-CHECK-OTHER-OUTLET", 1L, 102L, 1L, java.sql.Date.valueOf("2026-03-27"), "ORDERED",
                new BigDecimal("70.00"), BigDecimal.ZERO, new BigDecimal("70.00"));
        jdbcTemplate.update("""
                INSERT INTO procurement.goods_receipt (
                    receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                    status, total_amount, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "GR-CLOSE-CHECK-1", null, 1L, 101L, 1L, java.sql.Timestamp.from(Instant.parse("2026-03-27T10:00:00Z")),
                java.sql.Date.valueOf("2026-03-27"), "RECEIVED", new BigDecimal("110.00"));
        jdbcTemplate.update("""
                INSERT INTO procurement.goods_receipt (
                    receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                    status, total_amount, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "GR-CLOSE-CHECK-POSTED", null, 1L, 101L, 1L, java.sql.Timestamp.from(Instant.parse("2026-03-27T11:00:00Z")),
                java.sql.Date.valueOf("2026-03-27"), "POSTED", new BigDecimal("40.00"));
        jdbcTemplate.update("""
                INSERT INTO procurement.supplier_invoice (
                    invoice_number, supplier_id, region_id, outlet_id, currency_code, invoice_date, subtotal, tax_amount,
                    total_amount, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "INV-CLOSE-CHECK-1", 1L, 1L, 101L, "VND", java.sql.Date.valueOf("2026-03-27"),
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("110.00"), "APPROVED");
        jdbcTemplate.update("""
                INSERT INTO procurement.supplier_invoice (
                    invoice_number, supplier_id, region_id, outlet_id, currency_code, invoice_date, subtotal, tax_amount,
                    total_amount, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "INV-CLOSE-CHECK-CANCELLED", 1L, 1L, 101L, "VND", java.sql.Date.valueOf("2026-03-27"),
                new BigDecimal("20.00"), BigDecimal.ZERO, new BigDecimal("20.00"), "CANCELLED");

        mockMvc.perform(get("/internal/procurement/outlet-close-check")
                        .header("Authorization", serviceBearer(Set.of(PermissionCodes.PROCUREMENT_INTERNAL_READ), Set.of("procurement-service")))
                        .param("outletId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(101))
                .andExpect(jsonPath("$.blockingPurchaseOrders").value(1))
                .andExpect(jsonPath("$.blockingGoodsReceipts").value(1))
                .andExpect(jsonPath("$.blockingSupplierInvoices").value(1))
                .andExpect(jsonPath("$.hasBlockingDocuments").value(true));
    }

    @Test
    void shouldRejectOutletCloseCheckWithoutInternalPermission() throws Exception {
        mockMvc.perform(get("/internal/procurement/outlet-close-check")
                        .header("Authorization", bearer())
                        .param("outletId", "101"))
                .andExpect(status().isForbidden());
    }

    private ApprovedSupplierInvoiceFixture createApprovedSupplierInvoiceFixture(
            String supplierCode,
            String supplierName,
            String invoiceNumber,
            String goodsReceiptPostIdempotencyKey
    ) throws Exception {
        Long supplierId = createActiveSupplier(supplierCode, supplierName);

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
                        .header("Idempotency-Key", goodsReceiptPostIdempotencyKey))
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
                                  "invoiceNumber": "%s",
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
                                """.formatted(supplierId, invoiceNumber, goodsReceiptLineId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long supplierInvoiceId = readId(invoiceJson);

        mockMvc.perform(post("/supplier-invoices/{id}/approve", supplierInvoiceId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        return new ApprovedSupplierInvoiceFixture(supplierId, supplierInvoiceId);
    }

    private IssuedPurchaseOrderFixture createIssuedPurchaseOrderFixture(
            String supplierCode,
            String supplierName,
            String qtyOrdered
    ) throws Exception {
        Long supplierId = createActiveSupplier(supplierCode, supplierName);

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
                                      "qtyOrdered": %s,
                                      "expectedUnitPrice": 12.50,
                                      "taxPercent": 10.00
                                    }
                                  ]
                                }
                                """.formatted(supplierId, qtyOrdered)))
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

        return new IssuedPurchaseOrderFixture(
                supplierId,
                purchaseOrderId,
                objectMapper.readTree(issuedPoJson).get("lines").get(0).get("id").asLong()
        );
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

    private Long createGoodsReceipt(
            Long purchaseOrderId,
            Long purchaseOrderLineId,
            String qtyReceived,
            String receiptTime
    ) throws Exception {
        String receiptJson = mockMvc.perform(post("/goods-receipts")
                        .header("Authorization", bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "purchaseOrderId": %d,
                                  "receiptTime": "%s",
                                  "businessDate": "2026-03-27",
                                  "lines": [
                                    {
                                      "purchaseOrderLineId": %d,
                                      "ingredientId": 200,
                                      "uomCode": "KG",
                                      "qtyReceived": %s,
                                      "unitCost": 12.50
                                    }
                                  ]
                                }
                                """.formatted(purchaseOrderId, receiptTime, purchaseOrderLineId, qtyReceived)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readId(receiptJson);
    }

    private void receiveGoodsReceipt(Long goodsReceiptId) throws Exception {
        mockMvc.perform(post("/goods-receipts/{id}/receive", goodsReceiptId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    private String postGoodsReceipt(Long goodsReceiptId, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/goods-receipts/{id}/post", goodsReceiptId)
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andReturn().getResponse().getContentAsString();
    }

    private String outboxPayloadField(String aggregateType, String aggregateId, String eventType, String field) {
        return jdbcTemplate.queryForObject("""
                SELECT payload ->> '%s'
                FROM procurement.outbox_event
                WHERE aggregate_type = '%s'
                  AND aggregate_id = '%s'
                  AND event_type = '%s'
                ORDER BY created_at, id
                LIMIT 1
                """.formatted(field, aggregateType, aggregateId, eventType), String.class);
    }

    private Integer concurrentPaymentStatus(
            CountDownLatch start,
            CountDownLatch ready,
            Long supplierId,
            Long supplierInvoiceId,
            String amount,
            String idempotencyKey
    ) throws Exception {
        return concurrentPaymentResult(start, ready, supplierId, supplierInvoiceId, amount, idempotencyKey).status();
    }

    private PaymentCallResult concurrentPaymentResult(
            CountDownLatch start,
            CountDownLatch ready,
            Long supplierId,
            Long supplierInvoiceId,
            String amount,
            String idempotencyKey
    ) throws Exception {
        return concurrentPaymentResult(start, ready, supplierId, supplierInvoiceId, amount, amount, idempotencyKey);
    }

    private PaymentCallResult concurrentPaymentResult(
            CountDownLatch start,
            CountDownLatch ready,
            Long supplierId,
            Long supplierInvoiceId,
            String amount,
            String allocatedAmount,
            String idempotencyKey
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        var response = mockMvc.perform(post("/supplier-payments")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content("""
                                {
                                  "supplierId": %d,
                                  "currencyCode": "VND",
                                  "paymentMethod": "BANK_TRANSFER",
                                  "amount": %s,
                                  "paymentTime": "2026-03-27T12:00:00Z",
                                  "invoiceAllocations": [
                                    {
                                      "supplierInvoiceId": %d,
                                      "allocatedAmount": %s
                                    }
                                  ]
                                }
                                """.formatted(supplierId, amount, supplierInvoiceId, allocatedAmount)))
                .andReturn()
                .getResponse();
        return new PaymentCallResult(response.getStatus(), response.getContentAsString());
    }

    private record ApprovedSupplierInvoiceFixture(Long supplierId, Long supplierInvoiceId) {
    }

    private record IssuedPurchaseOrderFixture(Long supplierId, Long purchaseOrderId, Long purchaseOrderLineId) {
    }

    private record PaymentCallResult(int status, String body) {
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

    private String serviceBearer(Set<String> permissions, Set<String> audience) {
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
                "procurement-test-service-jti-" + permissions.hashCode() + "-" + audience.hashCode(),
                now,
                now.plus(jwtService.serviceTokenTtl()),
                com.fern.platform.common.FernPrincipalType.SERVICE,
                "org-service",
                audience
        ), jwtService.serviceTokenTtl());
        return "Bearer " + serviceToken;
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
                Instant.now().plusSeconds(900),
                com.fern.platform.common.FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("procurement-service")
        ), jwtService.accessTokenTtl());
    }
}
