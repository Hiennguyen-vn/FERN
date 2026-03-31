package com.fern.reportservice.service;

import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Projects expense-posted events into the {@code expense_fact} table,
 * then updates daily summaries.
 */
@Component
public class ExpenseEventProjector {
    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;

    public ExpenseEventProjector(ReportIngestionSupport support, DailySummaryProjector dailySummaryProjector) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
    }

    public void ingest(String payload, ExpensePostedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "finance.expense.posted",
                payload,
                () -> {
                    support.jdbcTemplate().update("""
                            INSERT INTO report.expense_fact (
                                fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                region_id, outlet_id, expense_record_id, employee_id, payroll_run_id, business_date, source_type, amount, payload
                            ) VALUES (
                                :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                :regionId, :outletId, :expenseRecordId, :employeeId, :payrollRunId, :businessDate, :sourceType, :amount, CAST(:payload AS jsonb)
                            )
                            ON CONFLICT DO NOTHING
                            """, support.params(
                            "factId", support.idGenerator().nextId(),
                            "sourceEventId", event.eventId(),
                            "sourceService", event.sourceService(),
                            "eventType", event.eventType(),
                            "occurredAt", event.occurredAt(),
                            "idempotencyKey", event.idempotencyKey(),
                            "regionId", event.regionId(),
                            "outletId", event.outletId(),
                            "expenseRecordId", event.expenseRecordId(),
                            "employeeId", event.employeeId(),
                            "payrollRunId", event.payrollRunId(),
                            "businessDate", event.businessDate(),
                            "sourceType", event.sourceType(),
                            "amount", event.amount(),
                            "payload", payload
                    ));
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
                            new SummaryDelta(BigDecimal.ZERO, BigDecimal.ZERO, event.amount(), BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }
}
