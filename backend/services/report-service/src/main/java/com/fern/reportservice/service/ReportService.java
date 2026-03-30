package com.fern.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
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
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReportService {
    private static final TypeReference<List<Map<String, Object>>> PREVIEW_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ReportExportService reportExportService;
    private final AtomicLong projectionLagMillis;

    public ReportService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator,
            Clock clock,
            TransactionTemplate transactionTemplate,
            ReportExportService reportExportService,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.reportExportService = reportExportService;
        this.projectionLagMillis = meterRegistry.gauge("fern_projection_consumer_lag", new AtomicLong(0));
    }

    public void ingestPosSaleCompleted(String payload, PosSaleCompletedEvent event) {
        ingestWithLanding(
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
                    BigDecimal totalSales = extractSnapshotLines(event.saleSnapshot()).stream()
                            .map(line -> decimalValue(line.get("lineTotal")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    applyDailySummaryDelta(
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
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestGoodsReceiptPosted(String payload, ProcurementGoodsReceiptPostedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "procurement.goods_receipt.posted",
                payload,
                () -> {
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
                    applyDailySummaryDelta(
                            event.eventId(),
                            event.sourceService(),
                            event.eventType(),
                            event.occurredAt(),
                            event.idempotencyKey(),
                            event.regionId(),
                            List.of(event.outletId()),
                            event.businessDate(),
                            payload,
                            new SummaryDelta(BigDecimal.ZERO, totalAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1)
                    );
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestAttendanceApproved(String payload, AttendanceApprovedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "attendance.approved",
                payload,
                () -> {
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
                    applyDailySummaryDelta(
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
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestPayrollCalculated(String payload, PayrollCalculatedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "payroll.calculated",
                payload,
                () -> {
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
                    BigDecimal totalPayroll = event.employees().stream()
                            .map(PayrollCalculatedEmployee::grossPay)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<Long> outletIds = event.employees().stream()
                            .map(PayrollCalculatedEmployee::outletId)
                            .distinct()
                            .toList();
                    applyDailySummaryDelta(
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
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestPayrollPosted(String payload, PayrollPostedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "payroll.posted",
                payload,
                () -> updateProjectionLag(event.occurredAt())
        );
    }

    public void ingestExpensePosted(String payload, ExpensePostedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "finance.expense.posted",
                payload,
                () -> persistExpensePosted(payload, event)
        );
    }

    void persistExpensePosted(String payload, ExpensePostedEvent event) {
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
        applyDailySummaryDelta(
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
        updateProjectionLag(event.occurredAt());
    }

    public void ingestInventoryAdjustmentPosted(String payload, InventoryAdjustmentPostedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "inventory.adjustment.posted",
                payload,
                () -> {
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
                    applyDailySummaryDelta(
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
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestWasteRecordPosted(String payload, WasteRecordPostedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "inventory.waste.posted",
                payload,
                () -> {
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
                    applyDailySummaryDelta(
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
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestStockCountPosted(String payload, StockCountPostedEvent event) {
        ingestWithLanding(
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "inventory.stock_count.posted",
                payload,
                () -> {
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
                    applyDailySummaryDelta(
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
                    updateProjectionLag(event.occurredAt());
                }
        );
    }

    public PayrollSummaryResponse payrollSummary(FernPrincipal principal, Long regionId, LocalDate fromDate, LocalDate toDate) {
        return reportExportService.payrollSummary(principal, regionId, fromDate, toDate);
    }

    public PayrollRunReportResponse payrollRun(FernPrincipal principal, Long runId) {
        return reportExportService.payrollRun(principal, runId);
    }

    public ExportJobResponse createExport(FernPrincipal principal, CreateExportRequest request, String idempotencyKey) {
        return reportExportService.createExport(principal, request, idempotencyKey);
    }

    @Transactional
    public ExportJobResponse createExport(FernPrincipal principal, CreateExportRequest request, String idempotencyKey, String correlationId) {
        return reportExportService.createExport(principal, request, idempotencyKey, correlationId);
    }

    public ExportJobResponse createPayrollExport(FernPrincipal principal, CreatePayrollExportRequest request, String idempotencyKey) {
        return reportExportService.createPayrollExport(principal, request, idempotencyKey);
    }

    public ExportJobResponse createPayrollExport(FernPrincipal principal, CreatePayrollExportRequest request, String idempotencyKey, String correlationId) {
        return reportExportService.createPayrollExport(principal, request, idempotencyKey, correlationId);
    }

    public ExportJobResponse getExport(FernPrincipal principal, Long jobId) {
        return reportExportService.getExport(principal, jobId);
    }

    public ExportPreviewResponse previewExport(FernPrincipal principal, Long jobId) {
        return reportExportService.previewExport(principal, jobId);
    }

    public ExportDownload downloadExport(FernPrincipal principal, Long jobId) {
        ReportExportService.ExportDownload download = reportExportService.downloadExport(principal, jobId);
        return new ExportDownload(download.resource(), download.fileName(), download.contentType());
    }

    @Scheduled(fixedDelayString = "${fern.report.export.worker-delay-ms:5000}")
    public void processQueuedExports() {
        reportExportService.processQueuedExports();
    }

    private void applyDailySummaryDelta(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            Long regionId,
            List<Long> outletIds,
            LocalDate businessDate,
            String payload
            ,
            SummaryDelta delta
    ) {
        long transactionIncrement = registerRegionEvent(regionId, businessDate, sourceEventId);
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
                    total_sales = report.region_daily_summary.total_sales + EXCLUDED.total_sales,
                    total_procurement = report.region_daily_summary.total_procurement + EXCLUDED.total_procurement,
                    total_expense = report.region_daily_summary.total_expense + EXCLUDED.total_expense,
                    total_payroll = report.region_daily_summary.total_payroll + EXCLUDED.total_payroll,
                    transaction_count = report.region_daily_summary.transaction_count + EXCLUDED.transaction_count,
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
                "totalSales", delta.totalSales(),
                "totalProcurement", delta.totalProcurement(),
                "totalExpense", delta.totalExpense(),
                "totalPayroll", delta.totalPayroll(),
                "transactionCount", transactionIncrement,
                "payload", payload
        ));
        long outletCountIncrement = registerCompanyOutlets(businessDate, outletIds);
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
                    total_sales = report.company_daily_summary.total_sales + EXCLUDED.total_sales,
                    total_procurement = report.company_daily_summary.total_procurement + EXCLUDED.total_procurement,
                    total_expense = report.company_daily_summary.total_expense + EXCLUDED.total_expense,
                    total_payroll = report.company_daily_summary.total_payroll + EXCLUDED.total_payroll,
                    outlet_count = report.company_daily_summary.outlet_count + EXCLUDED.outlet_count,
                    payload = EXCLUDED.payload
                """, params(
                "summaryId", idGenerator.nextId(),
                "sourceEventId", sourceEventId,
                "sourceService", sourceService,
                "eventType", eventType,
                "occurredAt", occurredAt,
                "idempotencyKey", idempotencyKey + ":company-summary",
                "businessDate", businessDate,
                "totalSales", delta.totalSales(),
                "totalProcurement", delta.totalProcurement(),
                "totalExpense", delta.totalExpense(),
                "totalPayroll", delta.totalPayroll(),
                "outletCount", outletCountIncrement,
                "payload", payload
        ));
    }

    private long registerRegionEvent(Long regionId, LocalDate businessDate, String sourceEventId) {
        return jdbcTemplate.update("""
                INSERT INTO report.region_daily_event (
                    region_id, business_date, source_event_id
                ) VALUES (
                    :regionId, :businessDate, :sourceEventId
                )
                ON CONFLICT DO NOTHING
                """, params(
                "regionId", regionId,
                "businessDate", businessDate,
                "sourceEventId", sourceEventId
        ));
    }

    private long registerCompanyOutlets(LocalDate businessDate, List<Long> outletIds) {
        if (outletIds == null || outletIds.isEmpty()) {
            return 0;
        }
        long inserted = 0;
        for (Long outletId : outletIds.stream().filter(item -> item != null).distinct().toList()) {
            inserted += jdbcTemplate.update("""
                    INSERT INTO report.company_daily_outlet (
                        business_date, outlet_id
                    ) VALUES (
                        :businessDate, :outletId
                    )
                    ON CONFLICT DO NOTHING
                    """, params(
                    "businessDate", businessDate,
                    "outletId", outletId
            ));
        }
        return inserted;
    }

    private void ingestWithLanding(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload,
            Runnable work
    ) {
        if (!Boolean.TRUE.equals(transactionTemplate.execute(status ->
                beginLanding(sourceEventId, sourceService, eventType, occurredAt, idempotencyKey, topic, payload)))) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                work.run();
                markLandingProcessed(sourceEventId);
            });
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> markLandingFailed(sourceEventId, exception));
            throw exception;
        }
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
        lockLandingKeys(sourceEventId, idempotencyKey);
        LandingRecord existing = findLandingRecord(sourceEventId, idempotencyKey);
        if (existing != null) {
            requireMatchingLanding(existing, sourceService, eventType, occurredAt, idempotencyKey, topic, payload);
            if (sourceEventId.equals(existing.sourceEventId()) && "FAILED".equals(existing.status())) {
                jdbcTemplate.update("""
                        UPDATE raw_events.event_landing
                        SET source_service = :sourceService,
                            event_type = :eventType,
                            occurred_at = :occurredAt,
                            ingested_at = CURRENT_TIMESTAMP,
                            idempotency_key = :idempotencyKey,
                            kafka_topic = :kafkaTopic,
                            payload = CAST(:payload AS jsonb),
                            status = 'RECEIVED',
                            processed_at = NULL,
                            error_message = NULL
                        WHERE source_event_id = :sourceEventId
                        """, params(
                        "sourceEventId", existing.sourceEventId(),
                        "sourceService", sourceService,
                        "eventType", eventType,
                        "occurredAt", occurredAt,
                        "idempotencyKey", idempotencyKey,
                        "kafkaTopic", topic,
                        "payload", payload
                ));
                return true;
            }
            return false;
        }
        jdbcTemplate.update("""
                INSERT INTO raw_events.event_landing (
                    landing_id, source_event_id, source_service, event_type, occurred_at, ingested_at,
                    idempotency_key, kafka_topic, payload, status
                ) VALUES (
                    :landingId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP,
                    :idempotencyKey, :kafkaTopic, CAST(:payload AS jsonb), 'RECEIVED'
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
    }

    private void markLandingProcessed(String sourceEventId) {
        jdbcTemplate.update("""
                UPDATE raw_events.event_landing
                SET status = 'PROCESSED',
                    processed_at = CURRENT_TIMESTAMP,
                    error_message = NULL
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId));
    }

    private void markLandingFailed(String sourceEventId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE raw_events.event_landing
                SET status = 'FAILED',
                    error_message = :errorMessage
                WHERE source_event_id = :sourceEventId
                """, params(
                "sourceEventId", sourceEventId,
                "errorMessage", ExceptionSummaries.safeSummary(exception)
        ));
    }

    private void lockLandingKeys(String sourceEventId, String idempotencyKey) {
        List<String> lockKeys = new ArrayList<>();
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            lockKeys.add("source:" + sourceEventId);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            lockKeys.add("idempotency:" + idempotencyKey);
        }
        lockKeys.stream()
                .distinct()
                .sorted()
                .forEach(lockKey -> jdbcTemplate.query(
                        "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                        params("lockKey", lockKey),
                        rs -> null
                ));
    }

    private LandingRecord findLandingRecord(String sourceEventId, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT source_event_id, source_service, event_type, occurred_at, idempotency_key, kafka_topic,
                       payload::text AS payload, status
                FROM raw_events.event_landing
                WHERE source_event_id = :sourceEventId
                   OR idempotency_key = :idempotencyKey
                ORDER BY landing_id
                LIMIT 1
                """, params(
                "sourceEventId", sourceEventId,
                "idempotencyKey", idempotencyKey
        ), rs -> rs.next()
                ? new LandingRecord(
                        rs.getString("source_event_id"),
                        rs.getString("source_service"),
                        rs.getString("event_type"),
                        instant(rs, "occurred_at"),
                        rs.getString("idempotency_key"),
                        rs.getString("kafka_topic"),
                        rs.getString("payload"),
                        rs.getString("status"))
                : null);
    }

    private void requireMatchingLanding(
            LandingRecord existing,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String topic,
            String payload
    ) {
        if (!Objects.equals(existing.sourceService(), sourceService)
                || !Objects.equals(existing.eventType(), eventType)
                || !Objects.equals(existing.occurredAt(), occurredAt)
                || !Objects.equals(existing.idempotencyKey(), idempotencyKey)
                || !Objects.equals(existing.kafkaTopic(), topic)
                || !jsonEquals(existing.payload(), payload)) {
            throw new IllegalStateException("Report landing idempotency conflict");
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

    private boolean jsonEquals(String left, String right) {
        try {
            var leftNode = objectMapper.readTree(left);
            var rightNode = objectMapper.readTree(right);
            if (leftNode.isObject()) {
                leftNode = leftNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) leftNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            if (rightNode.isObject()) {
                rightNode = rightNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) rightNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
                objectNode.remove("eventVersion");
            }
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to compare report landing payload", exception);
        }
    }

    private record SummaryDelta(
            BigDecimal totalSales,
            BigDecimal totalProcurement,
            BigDecimal totalExpense,
            BigDecimal totalPayroll,
            long transactionCount
    ) {
    }

    private record LandingRecord(
            String sourceEventId,
            String sourceService,
            String eventType,
            Instant occurredAt,
            String idempotencyKey,
            String kafkaTopic,
            String payload,
            String status
    ) {
    }

    public record ExportDownload(Resource resource, String fileName, String contentType) {
    }
}
