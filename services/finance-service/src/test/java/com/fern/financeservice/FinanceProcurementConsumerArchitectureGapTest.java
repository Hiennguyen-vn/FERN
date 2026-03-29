package com.fern.financeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.service.FinanceProcurementConsumer;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Tag("architecture-gap")
class FinanceProcurementConsumerArchitectureGapTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.projection-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.projection-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.projection-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "test-jwt-secret-that-is-at-least-32-characters-long");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private FinanceProcurementConsumer consumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @SpyBean(name = "operationalJdbcTemplate")
    @Qualifier("operationalJdbcTemplate")
    private NamedParameterJdbcTemplate operationalJdbcTemplate;

    @SpyBean(name = "projectionJdbcTemplate")
    @Qualifier("projectionJdbcTemplate")
    private NamedParameterJdbcTemplate projectionJdbcTemplate;

    @BeforeEach
    void setUp() {
        reset(operationalJdbcTemplate);
        reset(projectionJdbcTemplate);
        jdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    finance.outbox_event,
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
    void shouldRollbackProjectionWritesWhenSupplierPaymentProjectionFailsMidway() throws Exception {
        SupplierPaymentRecordedEvent event = new SupplierPaymentRecordedEvent(
                "payment-split-gap-1",
                "procurement.supplier.payment.recorded",
                Instant.parse("2026-03-27T12:00:00Z"),
                "procurement-service",
                "corr-payment-split-gap-1",
                "idem-payment-split-gap-1",
                9401L,
                7001L,
                Instant.parse("2026-03-27T12:00:00Z"),
                new BigDecimal("41.25"),
                "VND",
                List.of(new SupplierPaymentAllocation(8201L, new BigDecimal("41.25"))),
                3L
        );
        String payload = objectMapper.writeValueAsString(event);
        AtomicBoolean failOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("INSERT INTO finance_projection.reconciliation_snapshot")
                    && failOnce.compareAndSet(true, false)) {
                throw new DataIntegrityViolationException("forced reconciliation snapshot failure after posting projection write");
            }
            return invocation.callRealMethod();
        }).when(projectionJdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));

        assertThatThrownBy(() -> consumer.consumeSupplierPaymentRecorded(payload))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.accounting_posting_projection
                WHERE source_event_id = 'payment-split-gap-1'
                """))
                .as("Projection writes should not partially commit when the consumer fails halfway")
                .isZero();
        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.reconciliation_snapshot
                WHERE source_event_id = 'payment-split-gap-1'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.integration_event
                WHERE source_event_id = 'payment-split-gap-1'
                  AND status = 'FAILED'
                """)).isEqualTo(1);
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT error_message
                FROM finance.integration_event
                WHERE source_event_id = 'payment-split-gap-1'
                """, String.class)).isEqualTo("DataIntegrityViolationException");

        consumer.consumeSupplierPaymentRecorded(payload);

        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.accounting_posting_projection
                WHERE source_event_id = 'payment-split-gap-1'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.reconciliation_snapshot
                WHERE source_event_id = 'payment-split-gap-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.integration_event
                WHERE source_event_id = 'payment-split-gap-1'
                  AND status = 'PROCESSED'
                """)).isEqualTo(1);
    }

    @Test
    void shouldConvergeWhenMarkProcessedFailsAfterProjectionCommit() throws Exception {
        SupplierPaymentRecordedEvent event = new SupplierPaymentRecordedEvent(
                "payment-mark-gap-1",
                "procurement.supplier.payment.recorded",
                Instant.parse("2026-03-27T13:00:00Z"),
                "procurement-service",
                "corr-payment-mark-gap-1",
                "idem-payment-mark-gap-1",
                9402L,
                7001L,
                Instant.parse("2026-03-27T13:00:00Z"),
                new BigDecimal("52.10"),
                "VND",
                List.of(new SupplierPaymentAllocation(8202L, new BigDecimal("52.10"))),
                3L
        );
        String payload = objectMapper.writeValueAsString(event);
        AtomicBoolean failOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("UPDATE finance.integration_event")
                    && failOnce.compareAndSet(true, false)) {
                throw new DataAccessResourceFailureException("forced markIntegrationProcessed failure after projection commit");
            }
            return invocation.callRealMethod();
        }).when(operationalJdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));

        assertThatThrownBy(() -> consumer.consumeSupplierPaymentRecorded(payload))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.accounting_posting_projection
                WHERE source_event_id = 'payment-mark-gap-1'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.reconciliation_snapshot
                WHERE source_event_id = 'payment-mark-gap-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.integration_event
                WHERE source_event_id = 'payment-mark-gap-1'
                  AND status = 'FAILED'
                """)).isEqualTo(1);
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT error_message
                FROM finance.integration_event
                WHERE source_event_id = 'payment-mark-gap-1'
                """, String.class)).isEqualTo("DataAccessResourceFailureException");

        consumer.consumeSupplierPaymentRecorded(payload);

        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.accounting_posting_projection
                WHERE source_event_id = 'payment-mark-gap-1'
                """)).isEqualTo(1);
        assertThat(countProjection("""
                SELECT COUNT(*)
                FROM finance_projection.reconciliation_snapshot
                WHERE source_event_id = 'payment-mark-gap-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.integration_event
                WHERE source_event_id = 'payment-mark-gap-1'
                  AND status = 'PROCESSED'
                """)).isEqualTo(1);
    }

    @Test
    void shouldConvergeWhenGoodsReceiptOutboxEnqueueFailsAfterExpenseWrite() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                "gr-mark-gap-1",
                "procurement.goods_receipt.posted",
                Instant.parse("2026-03-27T14:00:00Z"),
                "procurement-service",
                "corr-gr-mark-gap-1",
                "idem-gr-mark-gap-1",
                9501L,
                8501L,
                1L,
                101L,
                java.time.LocalDate.of(2026, 3, 27),
                Instant.parse("2026-03-27T14:00:00Z"),
                3L,
                List.of(new GoodsReceiptPostedLine(200L, new BigDecimal("3.0000"), new BigDecimal("12.50"), 3001L))
        );
        String payload = objectMapper.writeValueAsString(event);
        AtomicBoolean failOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("INSERT INTO finance.outbox_event")
                    && failOnce.compareAndSet(true, false)) {
                throw new DataAccessResourceFailureException("forced expense outbox failure after expense write");
            }
            return invocation.callRealMethod();
        }).when(operationalJdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));

        assertThatThrownBy(() -> consumer.consumeGoodsReceiptPosted(payload))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.expense_record
                WHERE source_event_id = 'gr-mark-gap-1'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.integration_event
                WHERE source_event_id = 'gr-mark-gap-1'
                  AND status = 'FAILED'
                """)).isEqualTo(1);
        assertThat(jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT error_message
                FROM finance.integration_event
                WHERE source_event_id = 'gr-mark-gap-1'
                """, String.class)).isEqualTo("DataAccessResourceFailureException");

        consumer.consumeGoodsReceiptPosted(payload);

        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.expense_record
                WHERE source_event_id = 'gr-mark-gap-1'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.outbox_event
                WHERE aggregate_type = 'EXPENSE_RECORD'
                  AND aggregate_id IN (SELECT id::text FROM finance.expense_record WHERE source_event_id = 'gr-mark-gap-1')
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*)
                FROM finance.integration_event
                WHERE source_event_id = 'gr-mark-gap-1'
                  AND status = 'PROCESSED'
                """)).isEqualTo(1);
    }

    private int count(String sql) {
        return jdbcTemplate.getJdbcTemplate().queryForObject(sql, Integer.class);
    }

    private int countProjection(String sql) {
        return projectionJdbcTemplate.getJdbcTemplate().queryForObject(sql, Integer.class);
    }
}
