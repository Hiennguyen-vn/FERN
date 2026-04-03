package com.fern.reportservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.StockCountPostedEvent;
import com.fern.platform.contracts.WasteRecordPostedEvent;
import com.fern.reportservice.service.ReportDeserializationFailureRecorder;
import com.fern.reportservice.service.ReportService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportEventConsumer {
    private final ObjectMapper objectMapper;
    private final ReportService reportService;
    private final ReportDeserializationFailureRecorder failureRecorder;

    public ReportEventConsumer(
            ObjectMapper objectMapper,
            ReportService reportService,
            ReportDeserializationFailureRecorder failureRecorder
    ) {
        this.objectMapper = objectMapper;
        this.reportService = reportService;
        this.failureRecorder = failureRecorder;
    }

    @KafkaListener(topics = "pos.sale.completed")
    public void consumePosSaleCompleted(String payload) {
        reportService.ingestPosSaleCompleted(payload, read("pos.sale.completed", payload, PosSaleCompletedEvent.class));
    }

    @KafkaListener(topics = "procurement.goods_receipt.posted")
    public void consumeGoodsReceiptPosted(String payload) {
        reportService.ingestGoodsReceiptPosted(payload, read("procurement.goods_receipt.posted", payload, ProcurementGoodsReceiptPostedEvent.class));
    }

    @KafkaListener(topics = "attendance.approved")
    public void consumeAttendanceApproved(String payload) {
        reportService.ingestAttendanceApproved(payload, read("attendance.approved", payload, AttendanceApprovedEvent.class));
    }

    @KafkaListener(topics = "payroll.calculated")
    public void consumePayrollCalculated(String payload) {
        reportService.ingestPayrollCalculated(payload, read("payroll.calculated", payload, PayrollCalculatedEvent.class));
    }

    @KafkaListener(topics = "payroll.posted")
    public void consumePayrollPosted(String payload) {
        reportService.ingestPayrollPosted(payload, read("payroll.posted", payload, PayrollPostedEvent.class));
    }

    @KafkaListener(topics = "finance.expense.posted")
    public void consumeExpensePosted(String payload) {
        reportService.ingestExpensePosted(payload, read("finance.expense.posted", payload, ExpensePostedEvent.class));
    }

    @KafkaListener(topics = "inventory.adjustment.posted")
    public void consumeInventoryAdjustmentPosted(String payload) {
        reportService.ingestInventoryAdjustmentPosted(payload, read("inventory.adjustment.posted", payload, InventoryAdjustmentPostedEvent.class));
    }

    @KafkaListener(topics = "inventory.waste.posted")
    public void consumeWasteRecordPosted(String payload) {
        reportService.ingestWasteRecordPosted(payload, read("inventory.waste.posted", payload, WasteRecordPostedEvent.class));
    }

    @KafkaListener(topics = "inventory.stock_count.posted")
    public void consumeStockCountPosted(String payload) {
        reportService.ingestStockCountPosted(payload, read("inventory.stock_count.posted", payload, StockCountPostedEvent.class));
    }

    private <T> T read(String topic, String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            failureRecorder.record(topic, payload, type, exception);
            throw new IllegalArgumentException("Unable to deserialize " + type.getSimpleName(), exception);
        }
    }
}
