package com.fern.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class InventoryInboxReplayIntegrationTest {
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryEventConsumerService inventoryEventConsumerService;

    @Autowired
    private StockReservationService stockReservationService;

    @SpyBean
    private InventoryRepository inventoryRepository;

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
        reset(inventoryRepository);
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
    }

    @Test
    void shouldRetryFailedSaleInboxEvent() {
        SaleReservationResponse reservation = stockReservationService.reserveSale(
                servicePrincipal(Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)),
                new SaleReservationRequest(
                        101L,
                        LocalDate.of(2026, 3, 27),
                        5003L,
                        List.of(new SaleReservationItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
                ));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            String txnType = invocation.getArgument(5, String.class);
            String sourceReferenceId = invocation.getArgument(8, String.class);
            if ("SALE_USAGE".equals(txnType)
                    && "5003".equals(sourceReferenceId)
                    && failOnce.compareAndSet(true, false)) {
                throw new DataAccessResourceFailureException("forced sale usage failure");
            }
            return invocation.callRealMethod();
        }).when(inventoryRepository).appendTransaction(
                anyLong(),
                anyLong(),
                anyLong(),
                any(BigDecimal.class),
                any(LocalDate.class),
                anyString(),
                any(),
                anyString(),
                anyString(),
                anyLong()
        );

        PosSaleCompletedEvent event = new PosSaleCompletedEvent(
                "sale-event-retry-failed",
                "pos.sale.completed",
                Instant.parse("2026-03-27T10:35:00Z"),
                "pos-service",
                "corr-sale-retry-failed",
                "idem-sale-retry-failed",
                5003L,
                7003L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:35:00Z"),
                1L,
                reservation.reservationId(),
                List.of(),
                java.util.Map.of(),
                List.of(new RecipeUsageItem(200L, "ING-200", "Milk", "L", new BigDecimal("2.0000")))
        );

        assertThatThrownBy(() -> inventoryEventConsumerService.consumeSaleCompleted(event))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(inboxStatus("sale-event-retry-failed")).isEqualTo("FAILED");
        assertThat(inboxError("sale-event-retry-failed")).isEqualTo("DataAccessResourceFailureException");
        assertThat(transactionCount("SALE_USAGE", "5003")).isZero();

        inventoryEventConsumerService.consumeSaleCompleted(event);

        assertThat(inboxStatus("sale-event-retry-failed")).isEqualTo("PROCESSED");
        assertThat(transactionCount("SALE_USAGE", "5003")).isEqualTo(1);
    }

    @Test
    void shouldRetryFailedGoodsReceiptInboxEvent() {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            String txnType = invocation.getArgument(5, String.class);
            String sourceReferenceId = invocation.getArgument(8, String.class);
            if ("PURCHASE_IN".equals(txnType)
                    && "9401".equals(sourceReferenceId)
                    && failOnce.compareAndSet(true, false)) {
                throw new DataAccessResourceFailureException("forced purchase-in failure");
            }
            return invocation.callRealMethod();
        }).when(inventoryRepository).appendTransaction(
                anyLong(),
                anyLong(),
                anyLong(),
                any(BigDecimal.class),
                any(LocalDate.class),
                anyString(),
                any(BigDecimal.class),
                anyString(),
                anyString(),
                anyLong()
        );

        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                "receipt-event-retry-failed",
                "procurement.goods_receipt.posted",
                Instant.parse("2026-03-27T12:30:00Z"),
                "procurement-service",
                "corr-receipt-event-retry-failed",
                "idem-gr-retry-failed",
                9401L,
                8301L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T12:30:00Z"),
                2L,
                List.of(new GoodsReceiptPostedLine(200L, new BigDecimal("3.0000"), new BigDecimal("10000.00"), 3301L))
        );

        assertThatThrownBy(() -> inventoryEventConsumerService.consumeGoodsReceiptPosted(event))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(inboxStatus("receipt-event-retry-failed")).isEqualTo("FAILED");
        assertThat(inboxError("receipt-event-retry-failed")).isEqualTo("DataAccessResourceFailureException");
        assertThat(transactionCount("PURCHASE_IN", "9401")).isZero();

        inventoryEventConsumerService.consumeGoodsReceiptPosted(event);

        assertThat(inboxStatus("receipt-event-retry-failed")).isEqualTo("PROCESSED");
        assertThat(transactionCount("PURCHASE_IN", "9401")).isEqualTo(1);
    }

    private String inboxStatus(String sourceEventId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM inventory.inbox_event
                WHERE source_event_id = ?
                """, String.class, sourceEventId);
    }

    private String inboxError(String sourceEventId) {
        return jdbcTemplate.queryForObject("""
                SELECT error_message
                FROM inventory.inbox_event
                WHERE source_event_id = ?
                """, String.class, sourceEventId);
    }

    private int transactionCount(String txnType, String sourceReferenceId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM inventory.inventory_transaction
                WHERE txn_type = ? AND source_reference_id = ?
                """, Integer.class, txnType, sourceReferenceId);
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
