package com.fern.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollCalculatedEmployee;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.platform.contracts.StockCountPostedEvent;
import com.fern.platform.contracts.StockCountPostedLine;
import com.fern.platform.contracts.WasteRecordPostedEvent;
import com.fern.reportservice.config.ReportExportProperties;
import com.fern.reportservice.dto.ReportCommands.CreateExportRequest;
import com.fern.reportservice.dto.ReportCommands.CreatePayrollExportRequest;
import com.fern.reportservice.dto.ReportResponses.ExportJobResponse;
import com.fern.reportservice.dto.ReportResponses.ExportPreviewResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunAllocationResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunEmployeeResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunReportResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollSummaryResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final TypeReference<List<Map<String, Object>>> PREVIEW_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ReportAuthorizer reportAuthorizer;
    private final Clock clock;
    private final ReportExportProperties exportProperties;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final Counter exportFailureCounter;
    private final AtomicLong projectionLagMillis;

    public ReportService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator,
            ReportAuthorizer reportAuthorizer,
            Clock clock,
            ReportExportProperties exportProperties,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.reportAuthorizer = reportAuthorizer;
        this.clock = clock;
        this.exportProperties = exportProperties;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.exportFailureCounter = Counter.builder("fern_report_export_failures_total").register(meterRegistry);
        this.projectionLagMillis = meterRegistry.gauge("fern_projection_consumer_lag", new AtomicLong(0));
    }

    @Transactional
    public void ingestPosSaleCompleted(String payload, PosSaleCompletedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "pos.sale.completed", payload)) {
            return;
        }
        int lineNumber = 0;
        for (Map<String, Object> line : extractSnapshotLines(event.saleSnapshot())) {
            lineNumber++;
            jdbcTemplate.update("""
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
                    """, params(
                    "factId", idGenerator.nextId(),
                    "sourceEventId", event.eventId(),
                    "sourceService", event.sourceService(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "idempotencyKey", event.idempotencyKey() + ":sale:" + lineNumber,
                    "regionId", event.regionId(),
                    "outletId", event.outletId(),
                    "saleOrderId", event.saleOrderId(),
                    "productId", longValue(line.get("productId")),
                    "lineNumber", lineNumber,
                    "businessDate", event.businessDate(),
                    "qty", decimalValue(line.get("qty")),
                    "grossAmount", decimalValue(line.get("lineTotal")),
                    "discountAmount", decimalValue(line.get("discountAmount")),
                    "taxAmount", decimalValue(line.get("taxAmount")),
                    "netAmount", decimalValue(line.get("lineTotal")),
                    "payload", toJson(line)
            ));
        }
        for (SalePaymentSnapshot payment : event.payments()) {
            jdbcTemplate.update("""
                    INSERT INTO report.payment_fact (
                        fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, sale_order_id, payment_id, business_date, payment_method, payment_status, amount, payload
                    ) VALUES (
                        :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        :regionId, :outletId, :saleOrderId, :paymentId, :businessDate, :paymentMethod, :paymentStatus, :amount, CAST(:payload AS jsonb)
                    )
                    ON CONFLICT DO NOTHING
                    """, params(
                    "factId", idGenerator.nextId(),
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
                    "payload", toJson(payment)
            ));
        }
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
    }

    @Transactional
    public void ingestGoodsReceiptPosted(String payload, ProcurementGoodsReceiptPostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "procurement.goods_receipt.posted", payload)) {
            return;
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var line : event.lines()) {
            BigDecimal lineAmount = line.qtyReceived().multiply(line.unitCost());
            totalAmount = totalAmount.add(lineAmount);
            jdbcTemplate.update("""
                    INSERT INTO report.inventory_movement_fact (
                        fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                    ) VALUES (
                        :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
                    )
                    ON CONFLICT DO NOTHING
                    """, params(
                    "factId", idGenerator.nextId(),
                    "sourceEventId", event.eventId(),
                    "sourceService", event.sourceService(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "idempotencyKey", event.idempotencyKey() + ":movement:" + line.sourceLineId(),
                    "regionId", event.regionId(),
                    "outletId", event.outletId(),
                    "ingredientId", line.ingredientId(),
                    "businessDate", event.businessDate(),
                    "movementType", "PURCHASE_IN",
                    "qtyChange", line.qtyReceived(),
                    "unitCost", line.unitCost(),
                    "payload", toJson(line),
                    "sourceReferenceType", "GOODS_RECEIPT_LINE",
                    "sourceReferenceId", String.valueOf(line.sourceLineId())
            ));
        }
        jdbcTemplate.update("""
                INSERT INTO report.procurement_fact (
                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, goods_receipt_id, purchase_order_id, business_date, fact_amount, fact_type, reference_type, reference_id, payload
                ) VALUES (
                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :outletId, :goodsReceiptId, :purchaseOrderId, :businessDate, :factAmount, :factType, :referenceType, :referenceId, CAST(:payload AS jsonb)
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
                "goodsReceiptId", event.goodsReceiptId(),
                "purchaseOrderId", event.purchaseOrderId(),
                "businessDate", event.businessDate(),
                "factAmount", totalAmount,
                "factType", "GOODS_RECEIPT",
                "referenceType", "GOODS_RECEIPT",
                "referenceId", String.valueOf(event.goodsReceiptId()),
                "payload", payload
        ));
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
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
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
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
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
    }

    @Transactional
    public void ingestPayrollPosted(String payload, PayrollPostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "payroll.posted", payload)) {
            return;
        }
        updateProjectionLag(event.occurredAt());
    }

    @Transactional
    public void ingestExpensePosted(String payload, ExpensePostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "finance.expense.posted", payload)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO report.expense_fact (
                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, expense_record_id, employee_id, payroll_run_id, business_date, source_type, amount, payload
                ) VALUES (
                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :outletId, :expenseRecordId, :employeeId, :payrollRunId, :businessDate, :sourceType, :amount, CAST(:payload AS jsonb)
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
                "expenseRecordId", event.expenseRecordId(),
                "employeeId", event.employeeId(),
                "payrollRunId", event.payrollRunId(),
                "businessDate", event.businessDate(),
                "sourceType", event.sourceType(),
                "amount", event.amount(),
                "payload", payload
        ));
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
    }

    @Transactional
    public void ingestInventoryAdjustmentPosted(String payload, InventoryAdjustmentPostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "inventory.adjustment.posted", payload)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO report.inventory_movement_fact (
                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                ) VALUES (
                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
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
                "ingredientId", event.ingredientId(),
                "businessDate", event.businessDate(),
                "movementType", event.qtyChange().signum() >= 0 ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT",
                "qtyChange", event.qtyChange(),
                "unitCost", event.unitCost(),
                "payload", payload,
                "sourceReferenceType", event.sourceReferenceType(),
                "sourceReferenceId", event.sourceReferenceId()
        ));
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
    }

    @Transactional
    public void ingestWasteRecordPosted(String payload, WasteRecordPostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "inventory.waste.posted", payload)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO report.inventory_movement_fact (
                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                ) VALUES (
                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
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
                "ingredientId", event.ingredientId(),
                "businessDate", event.businessDate(),
                "movementType", "WASTE_OUT",
                "qtyChange", event.qtyChange(),
                "unitCost", event.unitCost(),
                "payload", payload,
                "sourceReferenceType", event.sourceReferenceType(),
                "sourceReferenceId", event.sourceReferenceId()
        ));
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
    }

    @Transactional
    public void ingestStockCountPosted(String payload, StockCountPostedEvent event) {
        if (!beginLanding(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), "inventory.stock_count.posted", payload)) {
            return;
        }
        for (StockCountPostedLine line : event.lines()) {
            jdbcTemplate.update("""
                    INSERT INTO report.inventory_movement_fact (
                        fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                        region_id, outlet_id, ingredient_id, business_date, movement_type, qty_change, unit_cost, payload, source_reference_type, source_reference_id
                    ) VALUES (
                        :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                        :regionId, :outletId, :ingredientId, :businessDate, :movementType, :qtyChange, :unitCost, CAST(:payload AS jsonb), :sourceReferenceType, :sourceReferenceId
                    )
                    ON CONFLICT DO NOTHING
                    """, params(
                    "factId", idGenerator.nextId(),
                    "sourceEventId", event.eventId(),
                    "sourceService", event.sourceService(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "idempotencyKey", event.idempotencyKey() + ":line:" + line.ingredientId(),
                    "regionId", event.regionId(),
                    "outletId", event.outletId(),
                    "ingredientId", line.ingredientId(),
                    "businessDate", event.businessDate(),
                    "movementType", line.varianceQty().signum() >= 0 ? "STOCK_ADJUSTMENT_IN" : "STOCK_ADJUSTMENT_OUT",
                    "qtyChange", line.varianceQty(),
                    "unitCost", line.unitCost(),
                    "payload", toJson(line),
                    "sourceReferenceType", "STOCK_COUNT_SESSION",
                    "sourceReferenceId", String.valueOf(event.stockCountSessionId())
            ));
        }
        refreshDailySummaries(event.eventId(), event.sourceService(), event.eventType(), event.occurredAt(), event.idempotencyKey(), event.regionId(), event.businessDate(), payload);
        updateProjectionLag(event.occurredAt());
    }

    public PayrollSummaryResponse payrollSummary(FernPrincipal principal, Long regionId, LocalDate fromDate, LocalDate toDate) {
        reportAuthorizer.requireRegionPayrollRead(principal, regionId);
        SummaryRow payroll = payrollSummaryRow(regionId, fromDate, toDate);
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
        Long regionId = requiredPayrollRunRegion(runId);
        reportAuthorizer.requireRegionPayrollRead(principal, regionId);
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
    public ExportJobResponse createExport(FernPrincipal principal, CreateExportRequest request, String idempotencyKey) {
        ExportSpec spec = buildExportSpec(request);
        authorizeExportRequest(principal, spec);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        ExportJobRecord existing = findExportByIdempotencyKey(normalizedIdempotencyKey);
        if (existing != null) {
            authorizeExportRecord(principal, existing);
            return toExportJobResponse(existing);
        }
        long jobId = idGenerator.nextId();
        Instant requestedAt = clock.instant();
        jdbcTemplate.update("""
                INSERT INTO report.export_job (
                    export_job_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    report_type, format, status, requested_by, requested_at, payload
                ) VALUES (
                    :exportJobId, :sourceEventId, 'report-service', 'report.export.queued', :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :reportType, :format, 'QUEUED', :requestedBy, :requestedAt, CAST(:payload AS jsonb)
                )
                """, params(
                "exportJobId", jobId,
                "sourceEventId", "report-export-" + jobId,
                "occurredAt", requestedAt,
                "idempotencyKey", normalizedIdempotencyKey,
                "reportType", spec.dataset().name(),
                "format", spec.format(),
                "requestedBy", principal == null ? null : principal.username(),
                "requestedAt", requestedAt,
                "payload", toJson(request)
        ));
        return getExport(principal, jobId);
    }

    public ExportJobResponse createPayrollExport(FernPrincipal principal, CreatePayrollExportRequest request, String idempotencyKey) {
        return createExport(principal, new CreateExportRequest(
                Dataset.PAYROLL_SUMMARY.name(),
                "CSV",
                request.regionId(),
                null,
                request.fromDate(),
                request.toDate(),
                null,
                null
        ), idempotencyKey);
    }

    public ExportJobResponse getExport(FernPrincipal principal, Long jobId) {
        ExportJobRecord record = requireExportJob(jobId);
        authorizeExportRecord(principal, record);
        return toExportJobResponse(record);
    }

    public ExportPreviewResponse previewExport(FernPrincipal principal, Long jobId) {
        ExportJobRecord record = requireExportJob(jobId);
        authorizeExportRecord(principal, record);
        return new ExportPreviewResponse(
                record.exportJobId(),
                record.status(),
                record.dataset(),
                record.rowCount(),
                parsePreview(record.previewPayload())
        );
    }

    public ExportDownload downloadExport(FernPrincipal principal, Long jobId) {
        ExportJobRecord record = requireExportJob(jobId);
        authorizeExportRecord(principal, record);
        if (!"COMPLETED".equals(record.status())) {
            throw new BadRequestException("Export job is not completed");
        }
        if (record.expiresAt() != null && record.expiresAt().isBefore(clock.instant())) {
            throw new ResourceNotFoundException("Export artifact has expired");
        }
        if (record.filePath() == null || record.filePath().isBlank()) {
            throw new ResourceNotFoundException("Export artifact is unavailable");
        }
        Path path = Paths.get(record.filePath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Export artifact is unavailable");
        }
        return new ExportDownload(new FileSystemResource(path), path.getFileName().toString(), "text/csv");
    }

    @Scheduled(fixedDelayString = "${fern.report.export.worker-delay-ms:5000}")
    @Transactional
    public void processQueuedExports() {
        List<Long> queuedJobIds = jdbcTemplate.query("""
                SELECT export_job_id
                FROM report.export_job
                WHERE status = 'QUEUED'
                ORDER BY requested_at, export_job_id
                LIMIT 10
                """, (rs, rowNum) -> rs.getLong("export_job_id"));
        for (Long jobId : queuedJobIds) {
            if (jdbcTemplate.update("""
                    UPDATE report.export_job
                    SET status = 'RUNNING', started_at = :startedAt
                    WHERE export_job_id = :jobId
                      AND status = 'QUEUED'
                    """, params("startedAt", clock.instant(), "jobId", jobId)) != 1) {
                continue;
            }
            try {
                completeExportJob(jobId);
            } catch (RuntimeException exception) {
                failExportJob(jobId, exception);
            }
        }
    }

    private void completeExportJob(Long jobId) {
        ExportJobRecord record = requireExportJob(jobId);
        ExportSpec spec = buildExportSpec(readValue(record.payload(), CreateExportRequest.class), record.dataset(), record.format());
        ExportData data = queryExportData(spec);
        Path filePath = writeCsv(record.exportJobId(), spec.dataset(), data.columns(), data.rows());
        List<Map<String, Object>> preview = data.rows().subList(0, Math.min(spec.previewLimit(), data.rows().size()));
        Instant completedAt = clock.instant();
        jdbcTemplate.update("""
                UPDATE report.export_job
                SET status = 'COMPLETED',
                    file_path = :filePath,
                    completed_at = :completedAt,
                    failed_at = NULL,
                    error_message = NULL,
                    row_count = :rowCount,
                    preview_payload = CAST(:previewPayload AS jsonb),
                    expires_at = :expiresAt,
                    checksum = :checksum
                WHERE export_job_id = :jobId
                  AND status = 'RUNNING'
                """, params(
                "filePath", filePath.toString(),
                "completedAt", completedAt,
                "rowCount", (long) data.rows().size(),
                "previewPayload", toJson(preview),
                "expiresAt", completedAt.plus(exportProperties.getArtifactRetentionDays(), ChronoUnit.DAYS),
                "checksum", sha256(filePath),
                "jobId", jobId
        ));
    }

    private void failExportJob(Long jobId, RuntimeException exception) {
        exportFailureCounter.increment();
        String errorSummary = ExceptionSummaries.safeSummary(exception);
        jdbcTemplate.update("""
                UPDATE report.export_job
                SET status = 'FAILED',
                    failed_at = :failedAt,
                    error_message = :errorMessage
                WHERE export_job_id = :jobId
                  AND status = 'RUNNING'
                """, params(
                "failedAt", clock.instant(),
                "errorMessage", errorSummary,
                "jobId", jobId
        ));
        operationalAlertPublisher.publish(
                "EXPORT_FAILED",
                "HIGH",
                "Report export failed for job " + jobId,
                null,
                null,
                null,
                "EXPORT_JOB",
                String.valueOf(jobId),
                Map.of("errorMessage", errorSummary)
        );
    }

    private ExportData queryExportData(ExportSpec spec) {
        return switch (spec.dataset()) {
            case SALES_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "sale_order_id", "line_number", "product_id", "qty", "gross_amount", "discount_amount", "tax_amount", "net_amount"),
                    """
                    SELECT business_date, region_id, outlet_id, sale_order_id, line_number, product_id, qty, gross_amount, discount_amount, tax_amount, net_amount
                    FROM report.sales_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, sale_order_id, line_number");
            case PAYMENT_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "sale_order_id", "payment_id", "payment_method", "payment_status", "amount"),
                    """
                    SELECT business_date, region_id, outlet_id, sale_order_id, payment_id, payment_method, payment_status, amount
                    FROM report.payment_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, sale_order_id, payment_id");
            case INVENTORY_MOVEMENT_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "ingredient_id", "movement_type", "qty_change", "unit_cost", "source_reference_type", "source_reference_id"),
                    """
                    SELECT business_date, region_id, outlet_id, ingredient_id, movement_type, qty_change, unit_cost, source_reference_type, source_reference_id
                    FROM report.inventory_movement_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, ingredient_id, source_reference_id");
            case PROCUREMENT_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "purchase_order_id", "goods_receipt_id", "fact_type", "fact_amount", "reference_type", "reference_id"),
                    """
                    SELECT business_date, region_id, outlet_id, purchase_order_id, goods_receipt_id, fact_type, fact_amount, reference_type, reference_id
                    FROM report.procurement_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, goods_receipt_id");
            case ATTENDANCE_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "employee_id", "attendance_status", "work_hours", "overtime_hours"),
                    """
                    SELECT business_date, region_id, outlet_id, employee_id, attendance_status, work_hours, overtime_hours
                    FROM report.attendance_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, employee_id");
            case PAYROLL_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "employee_id", "payroll_run_id", "gross_pay", "net_pay", "tax_amount"),
                    """
                    SELECT business_date, region_id, outlet_id, employee_id, payroll_run_id, gross_pay, net_pay, tax_amount
                    FROM report.payroll_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, payroll_run_id, employee_id");
            case EXPENSE_FACT -> tableExport(List.of("business_date", "region_id", "outlet_id", "expense_record_id", "employee_id", "payroll_run_id", "source_type", "amount"),
                    """
                    SELECT business_date, region_id, outlet_id, expense_record_id, employee_id, payroll_run_id, source_type, amount
                    FROM report.expense_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, expense_record_id");
            case REGION_DAILY_SUMMARY -> tableExport(List.of("business_date", "region_id", "total_sales", "total_procurement", "total_expense", "total_payroll", "transaction_count"),
                    """
                    SELECT business_date, region_id, total_sales, total_procurement, total_expense, total_payroll, transaction_count
                    FROM report.region_daily_summary
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date");
            case COMPANY_DAILY_SUMMARY -> queryCompanySummaryRows(spec);
            case PAYROLL_SUMMARY -> queryPayrollSummaryRows(spec);
            case PAYROLL_RUN -> queryPayrollRunRows(spec);
        };
    }

    private ExportData tableExport(List<String> columns, String sql, ExportSpec spec, String suffix) {
        StringBuilder builder = new StringBuilder(sql);
        MapSqlParameterSource parameters = params(
                "regionId", spec.regionId(),
                "fromDate", spec.fromDate(),
                "toDate", spec.toDate()
        );
        if (spec.outletId() != null) {
            builder.append(" AND outlet_id = :outletId");
            parameters.addValue("outletId", spec.outletId());
        }
        builder.append(suffix);
        return new ExportData(columns, jdbcTemplate.queryForList(builder.toString(), parameters));
    }

    private ExportData queryCompanySummaryRows(ExportSpec spec) {
        List<String> columns = List.of("business_date", "total_sales", "total_procurement", "total_expense", "total_payroll", "outlet_count");
        return new ExportData(columns, jdbcTemplate.queryForList("""
                SELECT business_date, total_sales, total_procurement, total_expense, total_payroll, outlet_count
                FROM report.company_daily_summary
                WHERE business_date BETWEEN :fromDate AND :toDate
                ORDER BY business_date
                """, params("fromDate", spec.fromDate(), "toDate", spec.toDate())));
    }

    private ExportData queryPayrollSummaryRows(ExportSpec spec) {
        PayrollSummaryResponse summary = payrollSummaryRowResponse(spec.regionId(), spec.fromDate(), spec.toDate());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region_id", summary.regionId());
        row.put("from_date", summary.fromDate());
        row.put("to_date", summary.toDate());
        row.put("total_gross_pay", summary.totalGrossPay());
        row.put("total_net_pay", summary.totalNetPay());
        row.put("total_tax", summary.totalTax());
        row.put("total_expense", summary.totalExpense());
        row.put("run_count", summary.runCount());
        return new ExportData(new ArrayList<>(row.keySet()), List.of(row));
    }

    private ExportData queryPayrollRunRows(ExportSpec spec) {
        List<String> columns = List.of("payroll_run_id", "employee_id", "outlet_id", "business_date", "gross_pay", "net_pay", "tax_amount", "allocated_expense_amount");
        return new ExportData(columns, jdbcTemplate.queryForList("""
                SELECT pf.payroll_run_id,
                       pf.employee_id,
                       pf.outlet_id,
                       pf.business_date,
                       pf.gross_pay,
                       pf.net_pay,
                       pf.tax_amount,
                       COALESCE(SUM(ef.amount), 0) AS allocated_expense_amount
                FROM report.payroll_fact pf
                LEFT JOIN report.expense_fact ef
                  ON ef.payroll_run_id = pf.payroll_run_id
                 AND ef.employee_id = pf.employee_id
                WHERE pf.payroll_run_id = :payrollRunId
                GROUP BY pf.payroll_run_id, pf.employee_id, pf.outlet_id, pf.business_date, pf.gross_pay, pf.net_pay, pf.tax_amount
                ORDER BY pf.employee_id
                """, params("payrollRunId", spec.payrollRunId())));
    }

    private void refreshDailySummaries(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            Long regionId,
            LocalDate businessDate,
            String payload
    ) {
        SummaryTotals totals = queryRegionSummaryTotals(regionId, businessDate);
        jdbcTemplate.update("""
                INSERT INTO report.region_daily_summary (
                    summary_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, business_date, total_sales, total_procurement, total_expense, total_payroll, transaction_count, payload
                ) VALUES (
                    :summaryId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :regionId, :businessDate, :totalSales, :totalProcurement, :totalExpense, :totalPayroll, :transactionCount, CAST(:payload AS jsonb)
                )
                ON CONFLICT (region_id, business_date) DO UPDATE
                SET source_event_id = EXCLUDED.source_event_id,
                    source_service = EXCLUDED.source_service,
                    event_type = EXCLUDED.event_type,
                    occurred_at = EXCLUDED.occurred_at,
                    ingested_at = CURRENT_TIMESTAMP,
                    idempotency_key = EXCLUDED.idempotency_key,
                    total_sales = EXCLUDED.total_sales,
                    total_procurement = EXCLUDED.total_procurement,
                    total_expense = EXCLUDED.total_expense,
                    total_payroll = EXCLUDED.total_payroll,
                    transaction_count = EXCLUDED.transaction_count,
                    payload = EXCLUDED.payload
                """, params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey + ":region-summary",
                "regionId", regionId,
                "businessDate", businessDate,
                "totalSales", totals.totalSales(),
                "totalProcurement", totals.totalProcurement(),
                "totalExpense", totals.totalExpense(),
                "totalPayroll", totals.totalPayroll(),
                "transactionCount", totals.transactionCount(),
                "payload", payload
        ));
        CompanySummaryTotals companyTotals = queryCompanySummaryTotals(businessDate);
        jdbcTemplate.update("""
                INSERT INTO report.company_daily_summary (
                    summary_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    business_date, total_sales, total_procurement, total_expense, total_payroll, outlet_count, payload
                ) VALUES (
                    :summaryId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :businessDate, :totalSales, :totalProcurement, :totalExpense, :totalPayroll, :outletCount, CAST(:payload AS jsonb)
                )
                ON CONFLICT (business_date) DO UPDATE
                SET source_event_id = EXCLUDED.source_event_id,
                    source_service = EXCLUDED.source_service,
                    event_type = EXCLUDED.event_type,
                    occurred_at = EXCLUDED.occurred_at,
                    ingested_at = CURRENT_TIMESTAMP,
                    idempotency_key = EXCLUDED.idempotency_key,
                    total_sales = EXCLUDED.total_sales,
                    total_procurement = EXCLUDED.total_procurement,
                    total_expense = EXCLUDED.total_expense,
                    total_payroll = EXCLUDED.total_payroll,
                    outlet_count = EXCLUDED.outlet_count,
                    payload = EXCLUDED.payload
                """, params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey + ":company-summary",
                "businessDate", businessDate,
                "totalSales", companyTotals.totalSales(),
                "totalProcurement", companyTotals.totalProcurement(),
                "totalExpense", companyTotals.totalExpense(),
                "totalPayroll", companyTotals.totalPayroll(),
                "outletCount", companyTotals.outletCount(),
                "payload", payload
        ));
    }

    private SummaryTotals queryRegionSummaryTotals(Long regionId, LocalDate businessDate) {
        return jdbcTemplate.query("""
                SELECT
                    COALESCE((SELECT SUM(net_amount) FROM report.sales_fact WHERE region_id = :regionId AND business_date = :businessDate), 0) AS total_sales,
                    COALESCE((SELECT SUM(fact_amount) FROM report.procurement_fact WHERE region_id = :regionId AND business_date = :businessDate), 0) AS total_procurement,
                    COALESCE((SELECT SUM(amount) FROM report.expense_fact WHERE region_id = :regionId AND business_date = :businessDate), 0) AS total_expense,
                    COALESCE((SELECT SUM(gross_pay) FROM report.payroll_fact WHERE region_id = :regionId AND business_date = :businessDate), 0) AS total_payroll,
                    COALESCE((
                        SELECT COUNT(DISTINCT source_event_id)
                        FROM (
                            SELECT source_event_id FROM report.sales_fact WHERE region_id = :regionId AND business_date = :businessDate
                            UNION ALL
                            SELECT source_event_id FROM report.payment_fact WHERE region_id = :regionId AND business_date = :businessDate
                            UNION ALL
                            SELECT source_event_id FROM report.inventory_movement_fact WHERE region_id = :regionId AND business_date = :businessDate
                            UNION ALL
                            SELECT source_event_id FROM report.procurement_fact WHERE region_id = :regionId AND business_date = :businessDate
                            UNION ALL
                            SELECT source_event_id FROM report.attendance_fact WHERE region_id = :regionId AND business_date = :businessDate
                            UNION ALL
                            SELECT source_event_id FROM report.payroll_fact WHERE region_id = :regionId AND business_date = :businessDate
                            UNION ALL
                            SELECT source_event_id FROM report.expense_fact WHERE region_id = :regionId AND business_date = :businessDate
                        ) fact_events
                    ), 0) AS transaction_count
                """, params("regionId", regionId, "businessDate", businessDate), rs -> rs.next()
                ? new SummaryTotals(
                rs.getBigDecimal("total_sales"),
                rs.getBigDecimal("total_procurement"),
                rs.getBigDecimal("total_expense"),
                rs.getBigDecimal("total_payroll"),
                rs.getLong("transaction_count")
        ) : new SummaryTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    private CompanySummaryTotals queryCompanySummaryTotals(LocalDate businessDate) {
        return jdbcTemplate.query("""
                SELECT
                    COALESCE((SELECT SUM(net_amount) FROM report.sales_fact WHERE business_date = :businessDate), 0) AS total_sales,
                    COALESCE((SELECT SUM(fact_amount) FROM report.procurement_fact WHERE business_date = :businessDate), 0) AS total_procurement,
                    COALESCE((SELECT SUM(amount) FROM report.expense_fact WHERE business_date = :businessDate), 0) AS total_expense,
                    COALESCE((SELECT SUM(gross_pay) FROM report.payroll_fact WHERE business_date = :businessDate), 0) AS total_payroll,
                    COALESCE((
                        SELECT COUNT(DISTINCT outlet_id)
                        FROM (
                            SELECT outlet_id FROM report.sales_fact WHERE business_date = :businessDate
                            UNION ALL
                            SELECT outlet_id FROM report.payment_fact WHERE business_date = :businessDate
                            UNION ALL
                            SELECT outlet_id FROM report.inventory_movement_fact WHERE business_date = :businessDate
                            UNION ALL
                            SELECT outlet_id FROM report.procurement_fact WHERE business_date = :businessDate
                            UNION ALL
                            SELECT outlet_id FROM report.attendance_fact WHERE business_date = :businessDate
                            UNION ALL
                            SELECT outlet_id FROM report.payroll_fact WHERE business_date = :businessDate
                            UNION ALL
                            SELECT outlet_id FROM report.expense_fact WHERE business_date = :businessDate
                        ) outlet_events
                    ), 0) AS outlet_count
                """, params("businessDate", businessDate), rs -> rs.next()
                ? new CompanySummaryTotals(
                rs.getBigDecimal("total_sales"),
                rs.getBigDecimal("total_procurement"),
                rs.getBigDecimal("total_expense"),
                rs.getBigDecimal("total_payroll"),
                rs.getLong("outlet_count")
        ) : new CompanySummaryTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    private PayrollSummaryResponse payrollSummaryRowResponse(Long regionId, LocalDate fromDate, LocalDate toDate) {
        SummaryRow row = payrollSummaryRow(regionId, fromDate, toDate);
        BigDecimal expense = jdbcTemplate.query("""
                SELECT COALESCE(SUM(amount), 0)
                FROM report.expense_fact
                WHERE region_id = :regionId
                  AND business_date BETWEEN :fromDate AND :toDate
                  AND source_type = 'PAYROLL'
                """, params("regionId", regionId, "fromDate", fromDate, "toDate", toDate), rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);
        return new PayrollSummaryResponse(regionId, fromDate, toDate, row.grossPay(), row.netPay(), row.taxAmount(), expense, row.runCount());
    }

    private SummaryRow payrollSummaryRow(Long regionId, LocalDate fromDate, LocalDate toDate) {
        return jdbcTemplate.query("""
                SELECT COALESCE(SUM(gross_pay), 0) AS gross_pay,
                       COALESCE(SUM(net_pay), 0) AS net_pay,
                       COALESCE(SUM(tax_amount), 0) AS tax_amount,
                       COUNT(DISTINCT payroll_run_id) AS run_count
                FROM report.payroll_fact
                WHERE region_id = :regionId
                  AND business_date BETWEEN :fromDate AND :toDate
                """, params("regionId", regionId, "fromDate", fromDate, "toDate", toDate), rs -> rs.next()
                ? new SummaryRow(
                rs.getBigDecimal("gross_pay"),
                rs.getBigDecimal("net_pay"),
                rs.getBigDecimal("tax_amount"),
                rs.getLong("run_count")
        ) : new SummaryRow(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0));
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

    private ExportSpec buildExportSpec(CreateExportRequest request) {
        return buildExportSpec(request, request.dataset(), request.format());
    }

    private ExportSpec buildExportSpec(CreateExportRequest request, String datasetValue, String formatValue) {
        Dataset dataset;
        try {
            dataset = Dataset.valueOf(datasetValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported dataset: " + datasetValue);
        }
        String format = formatValue == null || formatValue.isBlank() ? "CSV" : formatValue.toUpperCase(Locale.ROOT);
        if (!"CSV".equals(format)) {
            throw new BadRequestException("Only CSV exports are supported");
        }
        if (dataset == Dataset.COMPANY_DAILY_SUMMARY) {
            requireDateRange(request.fromDate(), request.toDate());
            return new ExportSpec(dataset, format, null, null, request.fromDate(), request.toDate(), null, previewLimit(request.limit()));
        }
        if (dataset == Dataset.PAYROLL_RUN) {
            if (request.payrollRunId() == null) {
                throw new BadRequestException("payrollRunId is required");
            }
            return new ExportSpec(dataset, format, requiredPayrollRunRegion(request.payrollRunId()), null, null, null, request.payrollRunId(), previewLimit(request.limit()));
        }
        if (request.regionId() == null) {
            throw new BadRequestException("regionId is required");
        }
        requireDateRange(request.fromDate(), request.toDate());
        return new ExportSpec(dataset, format, request.regionId(), request.outletId(), request.fromDate(), request.toDate(), request.payrollRunId(), previewLimit(request.limit()));
    }

    private void requireDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BadRequestException("fromDate and toDate are required");
        }
    }

    private int previewLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return exportProperties.getPreviewRowLimit();
        }
        return Math.min(requestedLimit, exportProperties.getPreviewRowLimit());
    }

    private void authorizeExportRequest(FernPrincipal principal, ExportSpec spec) {
        switch (spec.dataset()) {
            case PAYROLL_SUMMARY, PAYROLL_RUN, PAYROLL_FACT -> reportAuthorizer.requireRegionPayrollExport(principal, spec.regionId());
            case COMPANY_DAILY_SUMMARY -> reportAuthorizer.requireCompanyReportExport(principal);
            default -> reportAuthorizer.requireRegionReportExport(principal, spec.regionId());
        }
    }

    private void authorizeExportRecord(FernPrincipal principal, ExportJobRecord record) {
        Dataset dataset = Dataset.valueOf(record.dataset());
        Long regionId = extractRegionId(record);
        switch (dataset) {
            case PAYROLL_SUMMARY, PAYROLL_RUN, PAYROLL_FACT -> reportAuthorizer.requireRegionPayrollRead(principal, regionId);
            case COMPANY_DAILY_SUMMARY -> reportAuthorizer.requireCompanyReportRead(principal);
            default -> reportAuthorizer.requireRegionReportRead(principal, regionId);
        }
    }

    private Long extractRegionId(ExportJobRecord record) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.payload(), new TypeReference<>() {
            });
            Object region = payload.get("regionId");
            if (region != null) {
                return longValue(region);
            }
            Object runId = payload.get("payrollRunId");
            if (runId != null) {
                return requiredPayrollRunRegion(longValue(runId));
            }
            return null;
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to deserialize export payload");
        }
    }

    private ExportJobRecord requireExportJob(Long jobId) {
        ExportJobRecord record = jdbcTemplate.query("""
                SELECT export_job_id, report_type, format, status, requested_by, requested_at, started_at, completed_at, failed_at,
                       row_count, file_path, expires_at, error_message, payload::text AS payload, preview_payload::text AS preview_payload
                FROM report.export_job
                WHERE export_job_id = :jobId
                """, params("jobId", jobId), rs -> rs.next() ? mapExportJob(rs) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Export job not found");
        }
        return record;
    }

    private ExportJobRecord findExportByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT export_job_id, report_type, format, status, requested_by, requested_at, started_at, completed_at, failed_at,
                       row_count, file_path, expires_at, error_message, payload::text AS payload, preview_payload::text AS preview_payload
                FROM report.export_job
                WHERE idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? mapExportJob(rs) : null);
    }

    private ExportJobRecord mapExportJob(ResultSet rs) throws java.sql.SQLException {
        return new ExportJobRecord(
                rs.getLong("export_job_id"),
                rs.getString("report_type"),
                rs.getString("format"),
                rs.getString("status"),
                rs.getString("requested_by"),
                instant(rs, "requested_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                instant(rs, "failed_at"),
                rs.getObject("row_count") == null ? null : rs.getLong("row_count"),
                rs.getString("file_path"),
                instant(rs, "expires_at"),
                rs.getString("error_message"),
                rs.getString("payload"),
                rs.getString("preview_payload")
        );
    }

    private ExportJobResponse toExportJobResponse(ExportJobRecord record) {
        return new ExportJobResponse(
                record.exportJobId(),
                record.status(),
                record.dataset(),
                record.format(),
                record.requestedAt(),
                record.startedAt(),
                record.completedAt(),
                record.failedAt(),
                record.rowCount(),
                "COMPLETED".equals(record.status()) ? "/reports/exports/" + record.exportJobId() + "/download" : null,
                record.expiresAt(),
                record.errorMessage(),
                record.filePath(),
                parsePreview(record.previewPayload())
        );
    }

    private List<Map<String, Object>> parsePreview(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, PREVIEW_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to deserialize export preview");
        }
    }

    private Long requiredPayrollRunRegion(Long payrollRunId) {
        Long regionId = jdbcTemplate.query("""
                SELECT region_id
                FROM report.payroll_fact
                WHERE payroll_run_id = :payrollRunId
                LIMIT 1
                """, params("payrollRunId", payrollRunId), rs -> rs.next() ? rs.getLong("region_id") : null);
        if (regionId == null) {
            throw new ResourceNotFoundException("Payroll run report not found");
        }
        return regionId;
    }

    private Path writeCsv(Long jobId, Dataset dataset, List<String> columns, List<Map<String, Object>> rows) {
        try {
            Path baseDir = Paths.get(exportProperties.getBaseDir()).toAbsolutePath().normalize();
            Files.createDirectories(baseDir);
            Path path = baseDir.resolve(dataset.name().toLowerCase(Locale.ROOT) + "-" + jobId + ".csv");
            List<String> lines = new ArrayList<>();
            lines.add(String.join(",", columns));
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    values.add(escapeCsv(row.get(column)));
                }
                lines.add(String.join(",", values));
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
            return path;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write export artifact", exception);
        }
    }

    private String sha256(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] content = Files.readAllBytes(filePath);
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to checksum export artifact", exception);
        }
    }

    private List<Map<String, Object>> extractSnapshotLines(Map<String, Object> saleSnapshot) {
        Object value = saleSnapshot.get("lines");
        if (value == null) {
            return List.of();
        }
        return objectMapper.convertValue(value, PREVIEW_TYPE);
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private void updateProjectionLag(Instant occurredAt) {
        projectionLagMillis.set(Math.max(0, java.time.Duration.between(occurredAt, clock.instant()).toMillis()));
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank()
                ? "report-export:" + UUID.randomUUID()
                : idempotencyKey.trim();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String stringValue = value instanceof Map<?, ?> || value instanceof List<?>
                ? toJson(value)
                : value.toString();
        if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
            return "\"" + stringValue.replace("\"", "\"\"") + "\"";
        }
        return stringValue;
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

    private <T> T readValue(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to deserialize export request");
        }
    }

    private enum Dataset {
        SALES_FACT,
        PAYMENT_FACT,
        INVENTORY_MOVEMENT_FACT,
        PROCUREMENT_FACT,
        ATTENDANCE_FACT,
        PAYROLL_FACT,
        EXPENSE_FACT,
        REGION_DAILY_SUMMARY,
        COMPANY_DAILY_SUMMARY,
        PAYROLL_SUMMARY,
        PAYROLL_RUN
    }

    private record SummaryRow(BigDecimal grossPay, BigDecimal netPay, BigDecimal taxAmount, long runCount) {
    }

    private record SummaryTotals(
            BigDecimal totalSales,
            BigDecimal totalProcurement,
            BigDecimal totalExpense,
            BigDecimal totalPayroll,
            long transactionCount
    ) {
    }

    private record CompanySummaryTotals(
            BigDecimal totalSales,
            BigDecimal totalProcurement,
            BigDecimal totalExpense,
            BigDecimal totalPayroll,
            long outletCount
    ) {
    }

    private record ExportSpec(
            Dataset dataset,
            String format,
            Long regionId,
            Long outletId,
            LocalDate fromDate,
            LocalDate toDate,
            Long payrollRunId,
            int previewLimit
    ) {
    }

    private record ExportData(List<String> columns, List<Map<String, Object>> rows) {
    }

    private record ExportJobRecord(
            Long exportJobId,
            String dataset,
            String format,
            String status,
            String requestedBy,
            Instant requestedAt,
            Instant startedAt,
            Instant completedAt,
            Instant failedAt,
            Long rowCount,
            String filePath,
            Instant expiresAt,
            String errorMessage,
            String payload,
            String previewPayload
    ) {
    }

    public record ExportDownload(Resource resource, String fileName, String contentType) {
    }
}
