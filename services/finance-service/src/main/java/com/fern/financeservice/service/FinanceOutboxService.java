package com.fern.financeservice.service;

import static com.fern.financeservice.service.FinanceJdbcSupport.params;
import static com.fern.financeservice.service.FinancePrincipalSupport.actorId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.financeservice.service.payroll.model.PayrollPeriodRecord;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollAllocation;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollCalculatedEmployee;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PayrollPostedEvent.PayrollExpenseLink;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinanceOutboxService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FinanceOutboxService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void emitPayrollCalculated(PayrollPeriodRecord period, PayrollRunResponse run, FernPrincipal principal, String correlationId) {
        List<PayrollCalculatedEmployee> employees = run.employees().stream()
                .map(item -> new PayrollCalculatedEmployee(item.employeeId(), item.outletId(), item.grossPay(), item.deductionAmount(), item.taxAmount(), item.netPay()))
                .toList();
        List<PayrollAllocation> allocations = run.employees().stream()
                .flatMap(item -> item.allocations().stream().map(allocation -> new PayrollAllocation(item.employeeId(), allocation.outletId(), allocation.workHours(), allocation.allocatedAmount())))
                .toList();
        PayrollCalculatedEvent event = new PayrollCalculatedEvent(
                UUID.randomUUID().toString(),
                "payroll.calculated",
                clock.instant(),
                "finance-service",
                correlationId,
                UUID.randomUUID().toString(),
                run.id(),
                run.payrollPeriodId(),
                period.regionId(),
                run.runDate(),
                run.totalAmount(),
                actorId(principal),
                employees,
                allocations
        );
        enqueueOutbox("PAYROLL_RUN", run.id().toString(), "payroll.calculated", period.regionId().toString(), event);
    }

    public void emitPayrollPosted(
            PayrollPeriodRecord period,
            PayrollRunResponse run,
            FernPrincipal principal,
            String paymentReference,
            List<PayrollExpenseLink> links,
            String correlationId
    ) {
        PayrollPostedEvent event = new PayrollPostedEvent(
                UUID.randomUUID().toString(),
                "payroll.posted",
                clock.instant(),
                "finance-service",
                correlationId,
                UUID.randomUUID().toString(),
                run.id(),
                run.payrollPeriodId(),
                period.regionId(),
                run.runDate(),
                run.totalAmount(),
                paymentReference,
                actorId(principal),
                links
        );
        enqueueOutbox("PAYROLL_RUN", run.id().toString(), "payroll.posted", period.regionId().toString(), event);
    }

    public void emitExpensePosted(
            Long expenseRecordId,
            Long regionId,
            Long outletId,
            Long employeeId,
            Long payrollRunId,
            LocalDate businessDate,
            String sourceType,
            BigDecimal amount,
            String correlationId,
            String sourceReferenceType,
            String sourceReferenceId
    ) {
        ExpensePostedEvent event = new ExpensePostedEvent(
                UUID.randomUUID().toString(),
                "finance.expense.posted",
                clock.instant(),
                "finance-service",
                correlationId,
                UUID.randomUUID().toString(),
                expenseRecordId,
                regionId,
                outletId,
                employeeId,
                payrollRunId,
                businessDate,
                sourceType,
                amount,
                sourceReferenceType,
                sourceReferenceId
        );
        enqueueOutbox("EXPENSE_RECORD", expenseRecordId.toString(), "finance.expense.posted", regionId.toString(), event);
    }

    public void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        String payloadJson = toJson(payload);
        lockOutboxKey(aggregateType, aggregateId, eventType);
        OutboxEventRecord existing = findOutboxEvent(aggregateType, aggregateId, eventType);
        if (existing != null) {
            requireMatchingOutboxPayload(existing, payloadJson);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO finance.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', CURRENT_TIMESTAMP
                )
                """, params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "partitionKey", partitionKey,
                "payload", payloadJson
        ));
    }

    private void lockOutboxKey(String aggregateType, String aggregateId, String eventType) {
        String lockKey = aggregateType + ":" + aggregateId + ":" + eventType;
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                params("lockKey", lockKey),
                rs -> null
        );
    }

    private OutboxEventRecord findOutboxEvent(String aggregateType, String aggregateId, String eventType) {
        return jdbcTemplate.query("""
                SELECT aggregate_type, aggregate_id, event_type, payload::text AS payload
                FROM finance.outbox_event
                WHERE aggregate_type = :aggregateType
                  AND aggregate_id = :aggregateId
                  AND event_type = :eventType
                ORDER BY created_at, id
                LIMIT 1
                """, params(
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType
        ), rs -> rs.next()
                ? new OutboxEventRecord(
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"))
                : null);
    }

    private void requireMatchingOutboxPayload(OutboxEventRecord existing, String payloadJson) {
        if (!Objects.equals(existing.aggregateType(), "EXPENSE_RECORD")
                && !Objects.equals(existing.aggregateType(), "PAYROLL_RUN")) {
            return;
        }
        if (!jsonEqualsIgnoringEnvelope(existing.payload(), payloadJson)) {
            throw new IllegalStateException("Finance outbox idempotency conflict");
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private boolean jsonEqualsIgnoringEnvelope(String left, String right) {
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
            }
            if (rightNode.isObject()) {
                rightNode = rightNode.deepCopy();
                var objectNode = (com.fasterxml.jackson.databind.node.ObjectNode) rightNode;
                objectNode.remove("eventId");
                objectNode.remove("occurredAt");
                objectNode.remove("correlationId");
                objectNode.remove("idempotencyKey");
            }
            return leftNode.equals(rightNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to compare finance outbox payload", exception);
        }
    }

    private record OutboxEventRecord(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload
    ) {
    }
}
