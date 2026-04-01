package com.fern.reportservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.ExceptionSummaries;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.ScopeAccess;
import com.fern.platform.common.SnowflakeIdGenerator;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ReportExportService {
    private static final TypeReference<List<Map<String, Object>>> PREVIEW_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ReportAuthorizer reportAuthorizer;
    private final Clock clock;
    private final ReportExportProperties exportProperties;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final ReportAuditService reportAuditService;
    private final ExportArtifactStore exportArtifactStore;
    private final TransactionTemplate transactionTemplate;
    private final Counter exportFailureCounter;

    ReportExportService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator,
            ReportAuthorizer reportAuthorizer,
            Clock clock,
            ReportExportProperties exportProperties,
            OperationalAlertPublisher operationalAlertPublisher,
            ReportAuditService reportAuditService,
            ExportArtifactStore exportArtifactStore,
            TransactionTemplate transactionTemplate,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.reportAuthorizer = reportAuthorizer;
        this.clock = clock;
        this.exportProperties = exportProperties;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.reportAuditService = reportAuditService;
        this.exportArtifactStore = exportArtifactStore;
        this.transactionTemplate = transactionTemplate;
        this.exportFailureCounter = Counter.builder("fern_report_export_failures_total").register(meterRegistry);
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

    public ExportJobResponse createExport(FernPrincipal principal, CreateExportRequest request, String idempotencyKey) {
        return createExport(principal, request, idempotencyKey, null);
    }

    @Transactional
    public ExportJobResponse createExport(FernPrincipal principal, CreateExportRequest request, String idempotencyKey, String correlationId) {
        ExportSpec spec = buildExportSpec(request);
        authorizeExportRequest(principal, spec);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        ExportJobRecord existing = findExportByIdempotencyKey(normalizedIdempotencyKey);
        if (existing != null) {
            authorizeExportRecord(principal, existing);
            requireMatchingIdempotentExport(existing, spec);
            return toExportJobResponse(existing);
        }
        long jobId = idGenerator.nextId();
        Instant requestedAt = clock.instant();
        Long claimedJobId = jdbcTemplate.query("""
                INSERT INTO report.export_job (
                    export_job_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    report_type, format, status, requested_by, requested_at, correlation_id, payload
                ) VALUES (
                    :exportJobId, :sourceEventId, 'report-service', 'report.export.queued', :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    :reportType, :format, 'QUEUED', :requestedBy, :requestedAt, :correlationId, CAST(:payload AS jsonb)
                )
                ON CONFLICT (idempotency_key) DO UPDATE
                SET idempotency_key = report.export_job.idempotency_key
                RETURNING export_job_id
                """, params(
                "exportJobId", jobId,
                "sourceEventId", "report-export-" + jobId,
                "occurredAt", requestedAt,
                "idempotencyKey", normalizedIdempotencyKey,
                "reportType", spec.dataset().name(),
                "format", spec.format(),
                "requestedBy", principal == null ? null : principal.username(),
                "requestedAt", requestedAt,
                "correlationId", correlationId,
                "payload", toJson(request)
        ), rs -> rs.next() ? rs.getLong("export_job_id") : null);
        if (!Long.valueOf(jobId).equals(claimedJobId)) {
            ExportJobRecord concurrentExisting = findExportByIdempotencyKey(normalizedIdempotencyKey);
            if (concurrentExisting == null) {
                throw new IllegalStateException("Export job not found after idempotent upsert");
            }
            authorizeExportRecord(principal, concurrentExisting);
            requireMatchingIdempotentExport(concurrentExisting, spec);
            return toExportJobResponse(concurrentExisting);
        }
        ExportJobResponse response = getExport(principal, jobId);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("dataset", spec.dataset().name());
        auditPayload.put("format", spec.format());
        auditPayload.put("fromDate", spec.fromDate());
        auditPayload.put("toDate", spec.toDate());
        auditPayload.put("payrollRunId", spec.payrollRunId());
        reportAuditService.publish(
                "report.export.requested",
                principal,
                correlationId,
                spec.regionId(),
                spec.outletId(),
                "EXPORT_REQUEST",
                "EXPORT_JOB",
                String.valueOf(jobId),
                null,
                response,
                auditPayload
        );
        return response;
    }

    public ExportJobResponse createPayrollExport(FernPrincipal principal, CreatePayrollExportRequest request, String idempotencyKey) {
        return createPayrollExport(principal, request, idempotencyKey, null);
    }

    public ExportJobResponse createPayrollExport(FernPrincipal principal, CreatePayrollExportRequest request, String idempotencyKey, String correlationId) {
        return createExport(principal, new CreateExportRequest(
                Dataset.PAYROLL_SUMMARY.name(),
                "CSV",
                request.regionId(),
                null,
                request.fromDate(),
                request.toDate(),
                null,
                null
        ), idempotencyKey, correlationId);
    }

    public ExportJobResponse getExport(FernPrincipal principal, Long jobId) {
        ExportJobRecord record = requireExportJob(jobId);
        authorizeExportRecord(principal, record);
        return toExportJobResponse(record);
    }

    public PageResponse<ExportJobResponse> listExports(
            FernPrincipal principal,
            Integer page,
            Integer size,
            String dataset,
            String status,
            Long regionId,
            Long outletId
    ) {
        int safePage = page == null || page < 0 ? 0 : page;
        int clampedSize = ListQueryDefaults.clampLimit(size);
        long offset = ListQueryDefaults.offsetFrom(safePage, clampedSize);
        String normalizedDataset = dataset == null || dataset.isBlank() ? null : dataset.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        List<String> readableDatasets = readableDatasets(principal);
        if (readableDatasets.isEmpty()) {
            return new PageResponse<>(List.of(), safePage, clampedSize, false);
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("datasets", readableDatasets)
                .addValue("limit", clampedSize + 1)
                .addValue("offset", offset);
        StringBuilder sql = new StringBuilder("""
                SELECT export_job_id, report_type, format, status, requested_by, requested_at, started_at, completed_at, failed_at,
                       row_count, file_path, expires_at, error_message, correlation_id, payload::text AS payload, preview_payload::text AS preview_payload
                FROM report.export_job
                WHERE report_type IN (:datasets)
                """);
        if (normalizedDataset != null) {
            sql.append(" AND report_type = :dataset");
            parameters.addValue("dataset", normalizedDataset);
        }
        if (normalizedStatus != null) {
            sql.append(" AND status = :status");
            parameters.addValue("status", normalizedStatus);
        }
        if (regionId != null) {
            sql.append(" AND NULLIF(payload ->> 'regionId', '')::bigint = :regionId");
            parameters.addValue("regionId", regionId);
        }
        if (outletId != null) {
            sql.append(" AND NULLIF(payload ->> 'outletId', '')::bigint = :outletId");
            parameters.addValue("outletId", outletId);
        }
        if (!ScopeAccess.isSystemScoped(principal)) {
            List<Long> allowedRegionIds = principal.scopeRoots().regions().stream().distinct().toList();
            if (allowedRegionIds.isEmpty()) {
                return new PageResponse<>(List.of(), safePage, clampedSize, false);
            }
            sql.append(" AND NULLIF(payload ->> 'regionId', '')::bigint IN (:allowedRegionIds)");
            parameters.addValue("allowedRegionIds", allowedRegionIds);
        }
        sql.append("""
                 ORDER BY requested_at DESC, export_job_id DESC
                 LIMIT :limit OFFSET :offset
                """);

        List<ExportJobResponse> items = jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> toExportJobResponse(mapExportJob(rs)));
        return toPageResponseFromWindow(items, safePage, clampedSize);
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
        Resource resource = exportArtifactStore.resolve(record.filePath());
        if (resource == null) {
            throw new ResourceNotFoundException("Export artifact is unavailable");
        }
        return new ExportDownload(resource, exportArtifactStore.fileName(record.filePath()), "text/csv");
    }

    public void processQueuedExports() {
        for (Long jobId : claimQueuedExportJobs()) {
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
        ExportSnapshot export = exportData(record.exportJobId(), spec);
        Instant completedAt = clock.instant();
        try {
            completeExportJobRecord(record, spec, export.artifact(), export.preview(), export.rowCount(), completedAt);
        } catch (RuntimeException exception) {
            exportArtifactStore.deleteQuietly(export.artifact().path().toString());
            throw exception;
        }
    }

    private void failExportJob(Long jobId, RuntimeException exception) {
        exportFailureCounter.increment();
        String errorSummary = ExceptionSummaries.safeSummary(exception);
        ExportJobRecord record = transactionTemplate.execute(status -> {
            ExportJobRecord current = requireExportJob(jobId);
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
            return requireExportJob(jobId);
        });
        if (record != null && "FAILED".equals(record.status())) {
            reportAuditService.publish(
                    "report.export.failed",
                    null,
                    record.correlationId(),
                    extractRegionId(record),
                    extractOutletId(record),
                    "EXPORT_FAIL",
                    "EXPORT_JOB",
                    String.valueOf(record.exportJobId()),
                    null,
                    toExportJobResponse(record),
                    Map.of("errorMessage", errorSummary, "requestedBy", record.requestedBy())
            );
            exportArtifactStore.deleteQuietly(record.filePath());
        }
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

    private ExportSnapshot exportData(Long jobId, ExportSpec spec) {
        List<String> columns = exportColumns(spec.dataset());
        List<Map<String, Object>> preview = new ArrayList<>();
        int[] rowCount = {0};
        try (ExportArtifactStore.CsvArtifactWriter writer = exportArtifactStore.openCsv(
                exportBaseDir(),
                jobId,
                spec.dataset().name(),
                columns
        )) {
            streamExportData(spec, row -> {
                try {
                    writer.writeRow(row);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to write export row", exception);
                }
                if (preview.size() < spec.previewLimit()) {
                    preview.add(new LinkedHashMap<>(row));
                }
                rowCount[0]++;
            });
            return new ExportSnapshot(writer.finish(), preview, rowCount[0]);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate export artifact", exception);
        }
    }

    private List<String> exportColumns(Dataset dataset) {
        return switch (dataset) {
            case SALES_FACT -> List.of("business_date", "region_id", "outlet_id", "sale_order_id", "line_number", "product_id", "qty", "gross_amount", "discount_amount", "tax_amount", "net_amount");
            case PAYMENT_FACT -> List.of("business_date", "region_id", "outlet_id", "sale_order_id", "payment_id", "payment_method", "payment_status", "amount");
            case INVENTORY_MOVEMENT_FACT -> List.of("business_date", "region_id", "outlet_id", "ingredient_id", "movement_type", "qty_change", "unit_cost", "source_reference_type", "source_reference_id");
            case PROCUREMENT_FACT -> List.of("business_date", "region_id", "outlet_id", "purchase_order_id", "goods_receipt_id", "fact_type", "fact_amount", "reference_type", "reference_id");
            case ATTENDANCE_FACT -> List.of("business_date", "region_id", "outlet_id", "employee_id", "attendance_status", "work_hours", "overtime_hours");
            case PAYROLL_FACT -> List.of("business_date", "region_id", "outlet_id", "employee_id", "payroll_run_id", "gross_pay", "net_pay", "tax_amount");
            case EXPENSE_FACT -> List.of("business_date", "region_id", "outlet_id", "expense_record_id", "employee_id", "payroll_run_id", "source_type", "amount");
            case REGION_DAILY_SUMMARY -> List.of("business_date", "region_id", "total_sales", "total_procurement", "total_expense", "total_payroll", "transaction_count");
            case COMPANY_DAILY_SUMMARY -> List.of("business_date", "total_sales", "total_procurement", "total_expense", "total_payroll", "outlet_count");
            case PAYROLL_SUMMARY -> List.of("region_id", "from_date", "to_date", "total_gross_pay", "total_net_pay", "total_tax", "total_expense", "run_count");
            case PAYROLL_RUN -> List.of("payroll_run_id", "employee_id", "outlet_id", "business_date", "gross_pay", "net_pay", "tax_amount", "allocated_expense_amount");
        };
    }

    private void streamExportData(ExportSpec spec, Consumer<Map<String, Object>> rowConsumer) {
        switch (spec.dataset()) {
            case SALES_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, sale_order_id, line_number, product_id, qty, gross_amount, discount_amount, tax_amount, net_amount
                    FROM report.sales_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, sale_order_id, line_number",
                    rowConsumer);
            case PAYMENT_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, sale_order_id, payment_id, payment_method, payment_status, amount
                    FROM report.payment_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, sale_order_id, payment_id",
                    rowConsumer);
            case INVENTORY_MOVEMENT_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, ingredient_id, movement_type, qty_change, unit_cost, source_reference_type, source_reference_id
                    FROM report.inventory_movement_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, ingredient_id, source_reference_id",
                    rowConsumer);
            case PROCUREMENT_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, purchase_order_id, goods_receipt_id, fact_type, fact_amount, reference_type, reference_id
                    FROM report.procurement_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, goods_receipt_id",
                    rowConsumer);
            case ATTENDANCE_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, employee_id, attendance_status, work_hours, overtime_hours
                    FROM report.attendance_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, employee_id",
                    rowConsumer);
            case PAYROLL_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, employee_id, payroll_run_id, gross_pay, net_pay, tax_amount
                    FROM report.payroll_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, payroll_run_id, employee_id",
                    rowConsumer);
            case EXPENSE_FACT -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, outlet_id, expense_record_id, employee_id, payroll_run_id, source_type, amount
                    FROM report.expense_fact
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date, expense_record_id",
                    rowConsumer);
            case REGION_DAILY_SUMMARY -> streamTableExport(exportColumns(spec.dataset()),
                    """
                    SELECT business_date, region_id, total_sales, total_procurement, total_expense, total_payroll, transaction_count
                    FROM report.region_daily_summary
                    WHERE region_id = :regionId
                      AND business_date BETWEEN :fromDate AND :toDate
                    """,
                    spec,
                    " ORDER BY business_date",
                    rowConsumer);
            case COMPANY_DAILY_SUMMARY -> streamCompanySummaryRows(spec, rowConsumer);
            case PAYROLL_SUMMARY -> streamPayrollSummaryRows(spec, rowConsumer);
            case PAYROLL_RUN -> streamPayrollRunRows(spec, rowConsumer);
        }
    }

    private void streamTableExport(List<String> columns, String sql, ExportSpec spec, String suffix, Consumer<Map<String, Object>> rowConsumer) {
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
        streamRows(builder.toString(), parameters, columns, rowConsumer);
    }

    private void streamCompanySummaryRows(ExportSpec spec, Consumer<Map<String, Object>> rowConsumer) {
        streamRows("""
                SELECT business_date, total_sales, total_procurement, total_expense, total_payroll, outlet_count
                FROM report.company_daily_summary
                WHERE business_date BETWEEN :fromDate AND :toDate
                ORDER BY business_date
                """, params("fromDate", spec.fromDate(), "toDate", spec.toDate()), exportColumns(spec.dataset()), rowConsumer);
    }

    private void streamPayrollSummaryRows(ExportSpec spec, Consumer<Map<String, Object>> rowConsumer) {
        PayrollSummaryResponse summary = payrollSummarySnapshot(spec.regionId(), spec.fromDate(), spec.toDate());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region_id", summary.regionId());
        row.put("from_date", summary.fromDate());
        row.put("to_date", summary.toDate());
        row.put("total_gross_pay", summary.totalGrossPay());
        row.put("total_net_pay", summary.totalNetPay());
        row.put("total_tax", summary.totalTax());
        row.put("total_expense", summary.totalExpense());
        row.put("run_count", summary.runCount());
        rowConsumer.accept(row);
    }

    private PayrollSummaryResponse payrollSummarySnapshot(Long regionId, LocalDate fromDate, LocalDate toDate) {
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

    private void streamPayrollRunRows(ExportSpec spec, Consumer<Map<String, Object>> rowConsumer) {
        streamRows("""
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
                """, params("payrollRunId", spec.payrollRunId()), exportColumns(spec.dataset()), rowConsumer);
    }

    private void streamRows(
            String sql,
            MapSqlParameterSource parameters,
            List<String> columns,
            Consumer<Map<String, Object>> rowConsumer
    ) {
        jdbcTemplate.query(sql, parameters, (RowCallbackHandler) rs -> rowConsumer.accept(mapRow(rs, columns)));
    }

    private Map<String, Object> mapRow(ResultSet resultSet, List<String> columns) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : columns) {
            row.put(column, normalizeCellValue(resultSet.getObject(column)));
        }
        return row;
    }

    private Object normalizeCellValue(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return value;
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

    private Long extractOutletId(ExportJobRecord record) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.payload(), new TypeReference<>() {
            });
            Object outlet = payload.get("outletId");
            return outlet == null ? null : longValue(outlet);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to deserialize export payload");
        }
    }

    private boolean canInspectExportRecord(FernPrincipal principal, ExportJobRecord record) {
        try {
            authorizeExportRecord(principal, record);
            return true;
        } catch (ForbiddenException exception) {
            return false;
        }
    }

    private List<String> readableDatasets(FernPrincipal principal) {
        if (principal == null) {
            return List.of();
        }
        if (!principal.permissions().contains(PermissionCodes.REPORT_READ)
                && !principal.permissions().contains(PermissionCodes.REPORT_PAYROLL_READ)) {
            return List.of();
        }
        return java.util.Arrays.stream(Dataset.values())
                .map(Dataset::name)
                .filter(dataset -> ScopeAccess.isSystemScoped(principal) || !Dataset.COMPANY_DAILY_SUMMARY.name().equals(dataset))
                .collect(Collectors.toList());
    }

    private List<Long> claimQueuedExportJobs() {
        return transactionTemplate.execute(status -> jdbcTemplate.query("""
                UPDATE report.export_job job
                SET status = 'RUNNING',
                    started_at = COALESCE(job.started_at, :startedAt)
                FROM (
                    SELECT export_job_id
                    FROM report.export_job
                    WHERE status = 'QUEUED'
                    ORDER BY requested_at, export_job_id
                    LIMIT 10
                    FOR UPDATE SKIP LOCKED
                ) queued
                WHERE job.export_job_id = queued.export_job_id
                RETURNING job.export_job_id
                """, params("startedAt", clock.instant()), (rs, rowNum) -> rs.getLong("export_job_id")));
    }

    private void completeExportJobRecord(
            ExportJobRecord record,
            ExportSpec spec,
            ExportArtifactStore.ReportArtifact artifact,
            List<Map<String, Object>> preview,
            int rowCount,
            Instant completedAt
    ) {
        transactionTemplate.executeWithoutResult(status -> {
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
                    "filePath", artifact.path(),
                    "completedAt", completedAt,
                    "rowCount", (long) rowCount,
                    "previewPayload", toJson(preview),
                    "expiresAt", completedAt.plus(exportProperties.getArtifactRetentionDays(), ChronoUnit.DAYS),
                    "checksum", artifact.checksum(),
                    "jobId", record.exportJobId()
            ));
            ExportJobRecord completed = requireExportJob(record.exportJobId());
            reportAuditService.publish(
                    "report.export.completed",
                    null,
                    completed.correlationId(),
                    spec.regionId(),
                    spec.outletId(),
                    "EXPORT_COMPLETE",
                    "EXPORT_JOB",
                    String.valueOf(completed.exportJobId()),
                    record.status(),
                    toExportJobResponse(completed),
                    Map.of("rowCount", rowCount, "dataset", spec.dataset().name())
            );
        });
    }

    private String exportBaseDir() {
        return exportProperties.usesS3()
                ? exportProperties.storageLocation()
                : Paths.get(exportProperties.storageLocation()).toAbsolutePath().normalize().toString();
    }

    private ExportJobRecord requireExportJob(Long jobId) {
        ExportJobRecord record = jdbcTemplate.query("""
                SELECT export_job_id, report_type, format, status, requested_by, requested_at, started_at, completed_at, failed_at,
                       row_count, file_path, expires_at, error_message, correlation_id, payload::text AS payload, preview_payload::text AS preview_payload
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
                       row_count, file_path, expires_at, error_message, correlation_id, payload::text AS payload, preview_payload::text AS preview_payload
                FROM report.export_job
                WHERE idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? mapExportJob(rs) : null);
    }

    private void requireMatchingIdempotentExport(ExportJobRecord existing, ExportSpec requestedSpec) {
        ExportSpec existingSpec = buildExportSpec(readValue(existing.payload(), CreateExportRequest.class), existing.dataset(), existing.format());
        if (!existingSpec.equals(requestedSpec)) {
            throw new ConflictException("Idempotency-Key is already used for a different export request");
        }
    }

    private ExportJobRecord mapExportJob(ResultSet rs) throws SQLException {
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
                rs.getString("correlation_id"),
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

    private String normalizeIdempotencyKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank()
                ? "report-export:" + UUID.randomUUID()
                : idempotencyKey.trim();
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

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
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

    private <T> PageResponse<T> toPageResponse(List<T> items, Integer page, int size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int offset = Math.toIntExact(ListQueryDefaults.offsetFrom(page, size));
        if (offset >= items.size()) {
            return new PageResponse<>(List.of(), safePage, size, false);
        }
        int endExclusive = Math.min(items.size(), offset + size + 1);
        List<T> window = items.subList(offset, endExclusive);
        boolean hasMore = window.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(window.subList(0, size)) : List.copyOf(window);
        return new PageResponse<>(pagedItems, safePage, size, hasMore);
    }

    private <T> PageResponse<T> toPageResponseFromWindow(List<T> items, int page, int size) {
        boolean hasMore = items.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(items.subList(0, size)) : List.copyOf(items);
        return new PageResponse<>(pagedItems, page, size, hasMore);
    }

    public record ExportDownload(Resource resource, String fileName, String contentType) {
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

    private record ExportSnapshot(
            ExportArtifactStore.ReportArtifact artifact,
            List<Map<String, Object>> preview,
            int rowCount
    ) {
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
            String correlationId,
            String payload,
            String previewPayload
    ) {
    }
}
