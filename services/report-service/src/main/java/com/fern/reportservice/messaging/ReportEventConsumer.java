package com.fern.reportservice.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.reportservice.service.ReportService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportEventConsumer {
    private final ObjectMapper objectMapper;
    private final ReportService reportService;

    public ReportEventConsumer(ObjectMapper objectMapper, ReportService reportService) {
        this.objectMapper = objectMapper;
        this.reportService = reportService;
    }

    @KafkaListener(topics = "attendance.approved")
    public void consumeAttendanceApproved(String payload) {
        reportService.ingestAttendanceApproved(payload, read(payload, AttendanceApprovedEvent.class));
    }

    @KafkaListener(topics = "payroll.calculated")
    public void consumePayrollCalculated(String payload) {
        reportService.ingestPayrollCalculated(payload, read(payload, PayrollCalculatedEvent.class));
    }

    @KafkaListener(topics = "payroll.posted")
    public void consumePayrollPosted(String payload) {
        reportService.ingestPayrollPosted(payload, read(payload, PayrollPostedEvent.class));
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize " + type.getSimpleName(), exception);
        }
    }
}
