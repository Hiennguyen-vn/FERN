package com.fern.reportservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class SalesEventProjectorTest {

    private ReportIngestionSupport support;

    @Mock
    private DailySummaryProjector dailySummaryProjector;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private SnowflakeIdGenerator idGenerator;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

    private SalesEventProjector projector;

    @BeforeEach
    void setUp() {
        idGenerator = new SnowflakeIdGenerator(1);
        
        support = new ReportIngestionSupport(jdbcTemplate, new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()), idGenerator, null, null, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()) {
            @Override
            public void ingestWithLanding(String s1, String s2, String s3, Instant i, String s4, String s5, String s6, Runnable work) {
                work.run();
            }
            
            @Override
            public void updateProjectionLag(Instant occurredAt) {
                // no-op
            }
        };

        projector = new SalesEventProjector(support, dailySummaryProjector, new ObjectMapper());
    }

    @Test
    void shouldProjectSaleLinesAndPaymentsAndApplySummaryDelta() {
        // Given
        PosSaleCompletedEvent event = new PosSaleCompletedEvent(
                "sale-evt-1",
                "pos.sale.completed",
                Instant.parse("2026-03-31T12:00:00Z"),
                "pos-service",
                "corr-1",
                "idem-sale-1",
                1001L, // saleOrderId
                2001L, // sessionId
                1L,    // regionId
                101L,  // outletId
                LocalDate.parse("2026-03-31"),
                Instant.parse("2026-03-31T12:05:00Z"),
                5L,    // completedByUserId
                null,
                List.of(
                        new SalePaymentSnapshot(50L, "CASH", new BigDecimal("150.00"), "CAPTURED", Instant.parse("2026-03-31T12:04:00Z"), null)
                ),
                Map.of("lines", List.of(
                        Map.of("productId", 10, "qty", 2, "lineTotal", 100.00, "discountAmount", 0, "taxAmount", 10.00),
                        Map.of("productId", 11, "qty", 1, "lineTotal", 50.00, "discountAmount", 5.00, "taxAmount", 5.00)
                )),
                List.of()
        );

        String payload = "{}";

        // When
        projector.ingest(payload, event);

        // Then verify SQL inserts
        verify(jdbcTemplate, org.mockito.Mockito.times(3)).update(sqlCaptor.capture(), paramsCaptor.capture());
        
        // Should have 1 insert per line (2) + 1 insert per payment (1) = 3 inserts
        assertThat(sqlCaptor.getAllValues()).hasSize(3);

        // Verify Line 1 Insert
        assertThat(sqlCaptor.getAllValues().get(0)).contains("INSERT INTO report.sales_fact");
        MapSqlParameterSource line1Params = paramsCaptor.getAllValues().get(0);
        assertThat(line1Params.getValue("idempotencyKey")).isEqualTo("idem-sale-1:sale:1");
        assertThat(line1Params.getValue("productId")).isEqualTo(10L);
        assertThat(line1Params.getValue("grossAmount")).isEqualTo(new BigDecimal("100.0"));

        // Verify Line 2 Insert
        assertThat(sqlCaptor.getAllValues().get(1)).contains("INSERT INTO report.sales_fact");
        MapSqlParameterSource line2Params = paramsCaptor.getAllValues().get(1);
        assertThat(line2Params.getValue("idempotencyKey")).isEqualTo("idem-sale-1:sale:2");
        assertThat(line2Params.getValue("productId")).isEqualTo(11L);
        assertThat(line2Params.getValue("grossAmount")).isEqualTo(new BigDecimal("50.0"));

        // Verify Payment Insert
        assertThat(sqlCaptor.getAllValues().get(2)).contains("INSERT INTO report.payment_fact");
        MapSqlParameterSource pymtParams = paramsCaptor.getAllValues().get(2);
        assertThat(pymtParams.getValue("idempotencyKey")).isEqualTo("idem-sale-1:payment:50");
        assertThat(pymtParams.getValue("paymentMethod")).isEqualTo("CASH");
        assertThat(pymtParams.getValue("amount")).isEqualTo(new BigDecimal("150.00"));

        // Verify Daily Summary Delta
        ArgumentCaptor<SummaryDelta> deltaCaptor = ArgumentCaptor.forClass(SummaryDelta.class);
        verify(dailySummaryProjector).applyDelta(
                eq("sale-evt-1"),
                eq("pos-service"),
                eq("pos.sale.completed"),
                eq(Instant.parse("2026-03-31T12:00:00Z")),
                eq("idem-sale-1"),
                eq(1L),
                eq(List.of(101L)),
                eq(LocalDate.parse("2026-03-31")),
                eq(payload),
                deltaCaptor.capture()
        );

        SummaryDelta delta = deltaCaptor.getValue();
        // 100.0 + 50.0 = 150.0
        assertThat(delta.totalSales()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(delta.totalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(delta.transactionCount()).isEqualTo(1);

        // verify lag reported in fake is manually skipped, nothing to assert here
    }
}
