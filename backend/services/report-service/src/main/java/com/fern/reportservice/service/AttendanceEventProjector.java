package com.fern.reportservice.service;

import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.reportservice.service.DailySummaryProjector.SummaryDelta;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Projects attendance-approved events into the {@code attendance_fact} table,
 * then updates daily summaries.
 */
@Component
public class AttendanceEventProjector {
    private final ReportIngestionSupport support;
    private final DailySummaryProjector dailySummaryProjector;

    public AttendanceEventProjector(ReportIngestionSupport support, DailySummaryProjector dailySummaryProjector) {
        this.support = support;
        this.dailySummaryProjector = dailySummaryProjector;
    }

    public void ingest(String payload, AttendanceApprovedEvent event) {
        support.ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "attendance.approved",
                payload,
                () -> {
                    support.jdbcTemplate().update("""
                            INSERT INTO report.attendance_fact (
                                fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                region_id, outlet_id, employee_id, business_date, attendance_status, work_hours, overtime_hours, payload
                            ) VALUES (
                                :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                :regionId, :outletId, :employeeId, :businessDate, :attendanceStatus, :workHours, :overtimeHours, CAST(:payload AS jsonb)
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
                            "employeeId", event.employeeId(),
                            "businessDate", event.businessDate(),
                            "attendanceStatus", event.attendanceStatus(),
                            "workHours", event.workHours(),
                            "overtimeHours", event.overtimeHours(),
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
                            new SummaryDelta(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }
}
