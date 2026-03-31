package com.fern.reportservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Projects POS sale-completed events into {@code sales_fact} and {@code payment_fact} tables,
 * then updates daily summaries.
 */
@Component
public class SalesEventProjector {
    private static final TypeReference<List<Map<String, Object>>> PREVIEW_TYPE = new TypeReference<>() {
    };

    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;
    private final ObjectMapper objectMapper;

    public SalesEventProjector(
            ReportIngestionSupport support,
            DailySummaryProjector dailySummaryProjector,
            ObjectMapper objectMapper
    ) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
        this.objectMapper = objectMapper;
    }

    public void ingest(String payload, PosSaleCompletedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "pos.sale.completed",
                payload,
                () -> {
                    int lineNumber = 0;
                    for (Map<String, Object> line : extractSnapshotLines(event.saleSnapshot())) {
                        lineNumber++;
                        support.jdbcTemplate().update("""
                                INSERT INTO report.sales_fact (
                                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                    region_id, outlet_id, sale_order_id, product_id, line_number, business_date,
                                    qty, gross_amount, discount_amount, tax_amount, net_amount, payload
                                ) VALUES (
                                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                    :regionId, :outletId, :saleOrderId, :productId, :lineNumber, :businessDate,
                                    :qty, :grossAmount, :discountAmount, :taxAmount, :netAmount, CAST(:payload AS jsonb)
                                )
                                ON CONFLICT DO NOTHING
                                """, support.params(
                                "factId", support.idGenerator().nextId(),
                                "sourceEventId", event.eventId(),
                                "sourceService", event.sourceService(),
                                "eventType", event.eventType(),
                                "occurredAt", event.occurredAt(),
                                "idempotencyKey", event.idempotencyKey() + ":sale:" + lineNumber,
                                "regionId", event.regionId(),
                                "outletId", event.outletId(),
                                "saleOrderId", event.saleOrderId(),
                                "productId", support.longValue(line.get("productId")),
                                "lineNumber", lineNumber,
                                "businessDate", event.businessDate(),
                                "qty", support.decimalValue(line.get("qty")),
                                "grossAmount", support.decimalValue(line.get("lineTotal")),
                                "discountAmount", support.decimalValue(line.get("discountAmount")),
                                "taxAmount", support.decimalValue(line.get("taxAmount")),
                                "netAmount", support.decimalValue(line.get("lineTotal")),
                                "payload", support.toJson(line)
                        ));
                    }
                    for (SalePaymentSnapshot payment : event.payments()) {
                        support.jdbcTemplate().update("""
                                INSERT INTO report.payment_fact (
                                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                    region_id, outlet_id, sale_order_id, payment_id, business_date, payment_method, payment_status, amount, payload
                                ) VALUES (
                                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                    :regionId, :outletId, :saleOrderId, :paymentId, :businessDate, :paymentMethod, :paymentStatus, :amount, CAST(:payload AS jsonb)
                                )
                                ON CONFLICT DO NOTHING
                                """, support.params(
                                "factId", support.idGenerator().nextId(),
                                "sourceEventId", event.eventId(),
                                "sourceService", event.sourceService(),
                                "eventType", event.eventType(),
                                "occurredAt", event.occurredAt(),
                                "idempotencyKey", event.idempotencyKey() + ":payment:" + payment.paymentId(),
                                "regionId", event.regionId(),
                                "outletId", event.outletId(),
                                "saleOrderId", event.saleOrderId(),
                                "paymentId", payment.paymentId(),
                                "businessDate", event.businessDate(),
                                "paymentMethod", payment.paymentMethod(),
                                "paymentStatus", payment.status(),
                                "amount", payment.amount(),
                                "payload", support.toJson(payment)
                        ));
                    }
                    BigDecimal totalSales = extractSnapshotLines(event.saleSnapshot()).stream()
                            .map(line -> support.decimalValue(line.get("lineTotal")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    dailySummaryProjector.applyDelta(
                            event.eventId(),
                            event.sourceService(),
                            event.eventType(),
                            event.occurredAt(),
                            event.idempotencyKey(),
                            event.regionId(),
                            List.of(event.outletId()),
                            event.businessDate(),
                            payload,
                            new SummaryDelta(totalSales, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }

    private List<Map<String, Object>> extractSnapshotLines(Map<String, Object> saleSnapshot) {
        Object value = saleSnapshot.get("lines");
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, PREVIEW_TYPE);
    }
}
