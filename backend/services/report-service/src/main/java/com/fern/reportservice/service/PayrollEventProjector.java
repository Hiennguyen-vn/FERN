package com.fern.reportservice.service;

import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollCalculatedEmployee;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Projects payroll events ({@code payroll.calculated} and {@code payroll.posted})
 * into the {@code payroll_fact} table, then updates daily summaries.
 */
@Component
public class PayrollEventProjector {
    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;

    public PayrollEventProjector(ReportIngestionSupport support, DailySummaryProjector dailySummaryProjector) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
    }

    public void ingestCalculated(String payload, PayrollCalculatedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "payroll.calculated",
                payload,
                () -> {
                    for (PayrollCalculatedEmployee employee : event.employees()) {
                        support.jdbcTemplate().update("""
                                INSERT INTO report.payroll_fact (
                                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                    region_id, outlet_id, employee_id, payroll_run_id, business_date, gross_pay, net_pay, tax_amount, payload
                                ) VALUES (
                                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                    :regionId, :outletId, :employeeId, :payrollRunId, :businessDate, :grossPay, :netPay, :taxAmount, CAST(:payload AS jsonb)
                                )
                                ON CONFLICT DO NOTHING
                                """, support.params(
                                "factId", support.idGenerator().nextId(),
                                "sourceEventId", event.eventId(),
                                "sourceService", event.sourceService(),
                                "eventType", event.eventType(),
                                "occurredAt", event.occurredAt(),
                                "idempotencyKey", event.idempotencyKey() + ":" + employee.employeeId(),
                                "regionId", event.regionId(),
                                "outletId", employee.outletId(),
                                "employeeId", employee.employeeId(),
                                "payrollRunId", event.payrollRunId(),
                                "businessDate", event.businessDate(),
                                "grossPay", employee.grossPay(),
                                "netPay", employee.netPay(),
                                "taxAmount", employee.taxAmount(),
                                "payload", payload
                        ));
                    }
                    BigDecimal totalPayroll = event.employees().stream()
                            .map(PayrollCalculatedEmployee::grossPay)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<Long> outletIds = event.employees().stream()
                            .map(PayrollCalculatedEmployee::outletId)
                            .distinct()
                            .toList();
                    dailySummaryProjector.applyDelta(
                            event.eventId(),
                            event.sourceService(),
                            event.eventType(),
                            event.occurredAt(),
                            event.idempotencyKey(),
                            event.regionId(),
                            outletIds,
                            event.businessDate(),
                            payload,
                            new SummaryDelta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, totalPayroll, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestPosted(String payload, PayrollPostedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "payroll.posted",
                payload,
                () -> support.updateProjectionLag(event.occurredAt())
        );
    }
}
