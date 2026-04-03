package com.fern.reportservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.StockCountPostedEvent;
import com.fern.platform.contracts.SupplierInvoiceApprovedEvent;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.contracts.WasteRecordPostedEvent;
import com.fern.reportservice.dto.ReportCommands.CreateExportRequest;
import com.fern.reportservice.dto.ReportCommands.CreatePayrollExportRequest;
import com.fern.reportservice.dto.ReportResponses.ExportJobResponse;
import com.fern.reportservice.dto.ReportResponses.ExportPreviewResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollRunReportResponse;
import com.fern.reportservice.dto.ReportResponses.PayrollSummaryResponse;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin facade that delegates event ingestion to domain-specific projectors
 * and export operations to {@link ReportExportService}.
 *
 * <p>This class was refactored from a 1,073-line God class into a ~120-line
 * facade. The original responsibilities were extracted into:
 * <ul>
 *   <li>{@link ReportIngestionSupport} — landing zone, idempotency, utilities</li>
 *   <li>{@link DailySummaryProjector} — region/company daily summary UPSERT</li>
 *   <li>{@link SalesEventProjector} — POS sale completed events</li>
 *   <li>{@link ProcurementEventProjector} — goods receipt posted events</li>
 *   <li>{@link AttendanceEventProjector} — attendance approved events</li>
 *   <li>{@link PayrollEventProjector} — payroll calculated/posted events</li>
 *   <li>{@link ExpenseEventProjector} — expense posted events</li>
 *   <li>{@link InventoryEventProjector} — adjustment/waste/stock count events</li>
 * </ul>
 */
@Service
public class ReportService {
    private final SalesEventProjector salesProjector;
    private final ProcurementEventProjector procurementProjector;
    private final AttendanceEventProjector attendanceProjector;
    private final PayrollEventProjector payrollProjector;
    private final ExpenseEventProjector expenseProjector;
    private final InventoryEventProjector inventoryProjector;
    private final PayablesEventProjector payablesEventProjector;
    private final ReportExportService reportExportService;

    public ReportService(
            SalesEventProjector salesProjector,
            ProcurementEventProjector procurementProjector,
            AttendanceEventProjector attendanceProjector,
            PayrollEventProjector payrollProjector,
            ExpenseEventProjector expenseProjector,
            InventoryEventProjector inventoryProjector,
            PayablesEventProjector payablesEventProjector,
            ReportExportService reportExportService
    ) {
        this.salesProjector = salesProjector;
        this.procurementProjector = procurementProjector;
        this.attendanceProjector = attendanceProjector;
        this.payrollProjector = payrollProjector;
        this.expenseProjector = expenseProjector;
        this.inventoryProjector = inventoryProjector;
        this.payablesEventProjector = payablesEventProjector;
        this.reportExportService = reportExportService;
    }

    // ── Event ingestion (delegated to projectors) ─────────────────────────────

    public void ingestPosSaleCompleted(String payload, PosSaleCompletedEvent event) {
        salesProjector.ingest(payload, event);
    }

    public void ingestGoodsReceiptPosted(String payload, ProcurementGoodsReceiptPostedEvent event) {
        procurementProjector.ingest(payload, event);
    }

    public void ingestAttendanceApproved(String payload, AttendanceApprovedEvent event) {
        attendanceProjector.ingest(payload, event);
    }

    public void ingestPayrollCalculated(String payload, PayrollCalculatedEvent event) {
        payrollProjector.ingestCalculated(payload, event);
    }

    public void ingestPayrollPosted(String payload, PayrollPostedEvent event) {
        payrollProjector.ingestPosted(payload, event);
    }

    public void ingestExpensePosted(String payload, ExpensePostedEvent event) {
        expenseProjector.ingest(payload, event);
    }

    public void ingestInventoryAdjustmentPosted(String payload, InventoryAdjustmentPostedEvent event) {
        inventoryProjector.ingestAdjustment(payload, event);
    }

    public void ingestWasteRecordPosted(String payload, WasteRecordPostedEvent event) {
        inventoryProjector.ingestWaste(payload, event);
    }

    public void ingestStockCountPosted(String payload, StockCountPostedEvent event) {
        inventoryProjector.ingestStockCount(payload, event);
    }

    public void ingestSupplierInvoiceApproved(String payload, SupplierInvoiceApprovedEvent event) {
        payablesEventProjector.ingestSupplierInvoiceApproved(payload, event);
    }

    public void ingestSupplierPaymentRecorded(String payload, SupplierPaymentRecordedEvent event) {
        payablesEventProjector.ingestSupplierPaymentRecorded(payload, event);
    }

    // ── Export delegation ──────────────────────────────────────────────────────

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

    public PageResponse<ExportJobResponse> listExports(
            FernPrincipal principal,
            Integer page,
            Integer size,
            String dataset,
            String status,
            Long regionId,
            Long outletId
    ) {
        return reportExportService.listExports(principal, page, size, dataset, status, regionId, outletId);
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

    public record ExportDownload(Resource resource, String fileName, String contentType) {
    }
}
