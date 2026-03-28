package com.fern.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollAllocation;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollCalculatedEmployee;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PayrollPostedEvent.PayrollExpenseLink;
import com.fern.reportservice.dto.ReportCommands.CreatePayrollExportRequest;
import com.fern.reportservice.dto.ReportResponses.ExportJobResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunAllocationResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunEmployeeResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunReportResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollSummaryResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ReportAuthorizer reportAuthorizer;
    private final Clock clock;

    public ReportService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator,
            ReportAuthorizer reportAuthorizer,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.reportAuthorizer = reportAuthorizer;
        this.clock = clock;
    }

    @Transactional
    public void ingestAttendanceApproved(String payload, AttendanceApprovedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "attendance.approved", payload)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO report.attendance_fact (
                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, employee_id, business_date, attendance_status, work_hours, overtime_hours, payload
                ) VALUES (
                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :outletId, :employeeId, :businessDate, :attendanceStatus, :workHours, :overtimeHours, CAST(:payload AS jsonb)
                )
                ON CONFLICT DO NOTHING
                """, params(
                "factId", idGenerator.nextId(),
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
    }

    @Transactional
    public void ingestPayrollCalculated(String payload, PayrollCalculatedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "payroll.calculated", payload)) {
            return;
        }
        for (PayrollCalculatedEmployee employee : event.employees()) {
            jdbcTemplate.update("""
                    INSERT INTO report.payroll_fact (
                        fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, employee_id, payroll_run_id, business_date, gross_pay, net_pay, tax_amount, payload
                    ) VALUES (
                        :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        :regionId, :outletId, :employeeId, :payrollRunId, :businessDate, :grossPay, :netPay, :taxAmount, CAST(:payload AS jsonb)
                    )
                    ON CONFLICT DO NOTHING
                    """, params(
                    "factId", idGenerator.nextId(),
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
        insertSummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), event.totalAmount(), BigDecimal.ZERO, payload);
    }

    @Transactional
    public void ingestPayrollPosted(String payload, PayrollPostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "payroll.posted", payload)) {
            return;
        }
        for (PayrollExpenseLink expense : event.expenses()) {
            jdbcTemplate.update("""
                    INSERT INTO report.expense_fact (
                        fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, expense_record_id, employee_id, payroll_run_id, business_date, source_type, amount, payload
                    ) VALUES (
                        :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        :regionId, :outletId, :expenseRecordId, :employeeId, :payrollRunId, :businessDate, 'PAYROLL', :amount, CAST(:payload AS jsonb)
                    )
                    ON CONFLICT DO NOTHING
                    """, params(
                    "factId", idGenerator.nextId(),
                    "sourceEventId", event.eventId(),
                    "sourceService", event.sourceService(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "idempotencyKey", event.idempotencyKey() + ":" + expense.expenseRecordId(),
                    "regionId", event.regionId(),
                    "outletId", expense.outletId(),
                    "expenseRecordId", expense.expenseRecordId(),
                    "employeeId", expense.employeeId(),
                    "payrollRunId", event.payrollRunId(),
                    "businessDate", event.businessDate(),
                    "amount", expense.amount(),
                    "payload", payload
            ));
        }
        BigDecimal expenseTotal = event.expenses().stream().map(PayrollExpenseLink::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        insertSummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), event.totalAmount(), expenseTotal, payload);
    }

    public PayrollSummaryResponse payrollSummary(FernPrincipal principal, Long regionId, LocalDate fromDate, LocalDate toDate) {
        reportAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.REPORT_PAYROLL_READ);
        SummaryRow payroll = jdbcTemplate.query("""
                SELECT COALESCE(SUM(gross_pay), 0) AS gross_pay,
                       COALESCE(SUM(net_pay), 0) AS net_pay,
                       COALESCE(SUM(tax_amount), 0) AS tax_amount,
                       COUNT(DISTINCT payroll_run_id) AS run_count
                FROM report.payroll_fact
                WHERE region_id = :regionId
                  AND business_date BETWEEN :fromDate AND :toDate
                """, params("regionId", regionId, "fromDate", fromDate, "toDate", toDate), rs -> rs.next() ? new SummaryRow(
                rs.getBigDecimal("gross_pay"),
                rs.getBigDecimal("net_pay"),
                rs.getBigDecimal("tax_amount"),
                rs.getLong("run_count")
        ) : new SummaryRow(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
        BigDecimal expense = jdbcTemplate.query("""
                SELECT COALESCE(SUM(amount), 0)
                FROM report.expense_fact
                WHERE region_id = :regionId
                  AND business_date BETWEEN :fromDate AND :toDate
                  AND source_type = 'PAYROLL'
                """, params("regionId", regionId, "fromDate", fromDate, "toDate", toDate), rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);
        return new PayrollSummaryResponse(regionId, fromDate, toDate, payroll.grossPay(), payroll.netPay(), payroll.taxAmount(), expense, payroll.runCount());
    }

    public PayrollRunReportResponse payrollRun(FernPrincipal principal, Long runId) {
        Long regionId = jdbcTemplate.query("""
                SELECT region_id
                FROM report.payroll_fact
                WHERE payroll_run_id = :runId
                LIMIT 1
                """, params("runId", runId), rs -> rs.next() ? rs.getLong("region_id") : null);
        if (regionId == null) {
            throw new ResourceNotFoundException("Payroll run report not found");
        }
        reportAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.REPORT_PAYROLL_READ);
        List<PayrollRunEmployeeResponse> employees = jdbcTemplate.query("""
                SELECT employee_id, outlet_id, gross_pay, net_pay, tax_amount, business_date
                FROM report.payroll_fact
                WHERE payroll_run_id = :runId
                ORDER BY employee_id, outlet_id
                """, params("runId", runId), (rs, rowNum) -> new PayrollRunEmployeeResponse(
                rs.getLong("employee_id"),
                rs.getLong("outlet_id"),
                rs.getBigDecimal("gross_pay"),
                rs.getBigDecimal("net_pay"),
                rs.getBigDecimal("tax_amount"),
                rs.getObject("business_date", LocalDate.class)
        ));
        List<PayrollRunAllocationResponse> allocations = jdbcTemplate.query("""
                SELECT outlet_id, COALESCE(SUM(amount), 0) AS total_amount
                FROM report.expense_fact
                WHERE payroll_run_id = :runId
                GROUP BY outlet_id
                ORDER BY outlet_id
                """, params("runId", runId), (rs, rowNum) -> new PayrollRunAllocationResponse(
                rs.getLong("outlet_id"),
                rs.getBigDecimal("total_amount")
        ));
        return new PayrollRunReportResponse(runId, employees, allocations);
    }

    @Transactional
    public ExportJobResponse createExport(FernPrincipal principal, CreatePayrollExportRequest request) {
        reportAuthorizer.requireRegionPermission(principal, request.regionId(), PermissionCodes.REPORT_PAYROLL_EXPORT);
        long jobId = idGenerator.nextId();
        String filePath = "report://payroll/" + jobId + ".csv";
        jdbcTemplate.update("""
                INSERT INTO report.export_job (
                    export_job_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    report_type, format, status, requested_by, file_path, completed_at, payload
                ) VALUES (
                    :exportJobId, :sourceEventId, 'report-service', 'report.export.created', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :idempotencyKey,
                    'PAYROLL', 'CSV', 'COMPLETED', :requestedBy, :filePath, CURRENT_TIMESTAMP, CAST(:payload AS jsonb)
                )
                """, params(
                "exportJobId", jobId,
                "sourceEventId", "report-export-" + jobId,
                "idempotencyKey", "report-export-" + jobId,
                "requestedBy", principal.username(),
                "filePath", filePath,
                "payload", toJson(Map.of(
                        "regionId", request.regionId(),
                        "fromDate", request.fromDate(),
                        "toDate", request.toDate()
                ))
        ));
        return new ExportJobResponse(jobId, "COMPLETED", filePath, clock.instant());
    }

    private boolean beginLanding(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload
    ) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO raw_events.event_landing (
                        landing_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key, kafka_topic, payload
                    ) VALUES (
                        :landingId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey, :kafkaTopic, CAST(:payload AS jsonb)
                    )
                    """, params(
                    "landingId", idGenerator.nextId(),
                    "sourceEventId", sourceEventId,
                    "sourceService", sourceService,
                    "eventType", eventType,
                    "occurredAt", occurredAt,
                    "idempotencyKey", idempotencyKey,
                    "kafkaTopic", topic,
                    "payload", payload
            ));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private void insertSummaries(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            Long regionId,
            LocalDate businessDate,
            BigDecimal totalPayroll,
            BigDecimal totalExpense,
            String payload
    ) {
        jdbcTemplate.update("""
                INSERT INTO report.region_daily_summary (
                    summary_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, business_date, total_sales, total_procurement, total_expense, total_payroll, transaction_count, payload
                ) VALUES (
                    :summaryId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :businessDate, 0, 0, :totalExpense, :totalPayroll, 1, CAST(:payload AS jsonb)
                )
                ON CONFLICT (source_event_id) DO NOTHING
                """, params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey,
                "regionId", regionId,
                "businessDate", businessDate,
                "totalExpense", totalExpense,
                "totalPayroll", totalPayroll,
                "payload", payload
        ));
        jdbcTemplate.update("""
                INSERT INTO report.company_daily_summary (
                    summary_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    business_date, total_sales, total_procurement, total_expense, total_payroll, outlet_count, payload
                ) VALUES (
                    :summaryId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :businessDate, 0, 0, :totalExpense, :totalPayroll, 1, CAST(:payload AS jsonb)
                )
                ON CONFLICT (source_event_id) DO NOTHING
                """, params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey,
                "businessDate", businessDate,
                "totalExpense", totalExpense,
                "totalPayroll", totalPayroll,
                "payload", payload
        ));
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value == null) {
                parameters.addValue((String) values[index], null, Types.NULL);
            } else if (value instanceof Instant instant) {
                parameters.addValue((String) values[index], OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to serialize payload");
        }
    }

    private record SummaryRow(BigDecimal grossPay, BigDecimal netPay, BigDecimal taxAmount, long runCount) {
    }
}
