package com.fern.financeservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.service.FinanceProcurementConsumer;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class FinanceServiceIntegrationTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.projection-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.projection-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.projection-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private FinanceProcurementConsumer consumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("projectionJdbcTemplate")
    private NamedParameterJdbcTemplate projectionJdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    finance.expense_inventory_purchase,
                    finance.expense_record,
                    finance.integration_event
                RESTART IDENTITY CASCADE
                """);
        projectionJdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    finance_projection.accounting_posting_projection,
                    finance_projection.reconciliation_snapshot
                """);
    }

    @Test
    void shouldPersistGoodsReceiptExpenseIdempotently() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                "gr-event-1",
                "procurement.goods_receipt.posted",
                Instant.parse("2026-03-27T10:00:00Z"),
                "procurement-service",
                "corr-gr-1",
                "idem-gr-1",
                9001L,
                8001L,
                1L,
                101L,
                LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T10:00:00Z"),
                2L,
                List.of(new GoodsReceiptPostedLine(200L, new BigDecimal("3.0000"), new BigDecimal("12.50"), 3001L))
        );
        String payload = objectMapper.writeValueAsString(event);

        consumer.consumeGoodsReceiptPosted(payload);
        consumer.consumeGoodsReceiptPosted(payload);

        Integer expenseCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM finance.expense_record WHERE source_event_id = 'gr-event-1'
                """, Integer.class);
        Integer linkCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM finance.expense_inventory_purchase WHERE goods_receipt_id = 9001
                """, Integer.class);

        assertThat(expenseCount).isEqualTo(1);
        assertThat(linkCount).isEqualTo(1);
    }

    @Test
    void shouldPersistSupplierPaymentProjectionIdempotently() throws Exception {
        SupplierPaymentRecordedEvent event = new SupplierPaymentRecordedEvent(
                "payment-event-1",
                "procurement.supplier.payment.recorded",
                Instant.parse("2026-03-27T12:00:00Z"),
                "procurement-service",
                "corr-pay-1",
                "idem-pay-1",
                9101L,
                7001L,
                Instant.parse("2026-03-27T12:00:00Z"),
                new BigDecimal("41.25"),
                "VND",
                List.of(new SupplierPaymentAllocation(8201L, new BigDecimal("41.25"))),
                3L
        );
        String payload = objectMapper.writeValueAsString(event);

        consumer.consumeSupplierPaymentRecorded(payload);
        consumer.consumeSupplierPaymentRecorded(payload);

        Integer postingCount = projectionJdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM finance_projection.accounting_posting_projection WHERE source_event_id = 'payment-event-1'
                """, Integer.class);
        Integer snapshotCount = projectionJdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM finance_projection.reconciliation_snapshot WHERE source_event_id = 'payment-event-1'
                """, Integer.class);

        assertThat(postingCount).isEqualTo(1);
        assertThat(snapshotCount).isEqualTo(1);
    }
}
