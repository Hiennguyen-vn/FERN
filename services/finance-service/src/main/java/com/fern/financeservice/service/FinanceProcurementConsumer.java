package com.fern.financeservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceProcurementConsumer {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate projectionJdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    public FinanceProcurementConsumer(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("projectionJdbcTemplate") NamedParameterJdbcTemplate projectionJdbcTemplate,
            SnowflakeIdGenerator snowflakeIdGenerator,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectionJdbcTemplate = projectionJdbcTemplate;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "procurement.goods_receipt.posted", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeGoodsReceiptPosted(String payload) throws Exception {
        ProcurementGoodsReceiptPostedEvent event = objectMapper.readValue(payload, ProcurementGoodsReceiptPostedEvent.class);
        if (!beginIntegrationEvent(event.eventId(), event.eventType(), payload)) {
            return;
        }
        BigDecimal amount = event.lines().stream()
                .map(line -> line.qtyReceived().multiply(line.unitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long expenseRecordId = insertForId("""
                INSERT INTO finance.expense_record (
                    region_id, outlet_id, employee_id, expense_time, amount, source_type, status, note,
                    submitted_by_user_id, approved_by_user_id, created_at, updated_at, source_event_id,
                    source_reference_type, source_reference_id, posted_at
                ) VALUES (
                    :regionId, :outletId, NULL, :expenseTime, :amount, 'INVENTORY_PURCHASE', 'POSTED', :note,
                    :submittedByUserId, :approvedByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :sourceEventId,
                    'GOODS_RECEIPT', :sourceReferenceId, :postedAt
                )
                """, params(
                "regionId", event.regionId(),
                "outletId", event.outletId(),
                "expenseTime", event.postedAt(),
                "amount", amount,
                "note", "Goods receipt " + event.goodsReceiptId() + " posted from procurement-service",
                "submittedByUserId", event.postedByUserId(),
                "approvedByUserId", event.postedByUserId(),
                "sourceEventId", event.eventId(),
                "sourceReferenceId", event.goodsReceiptId().toString(),
                "postedAt", event.postedAt()
        ));
        jdbcTemplate.update("""
                INSERT INTO finance.expense_inventory_purchase (expense_record_id, goods_receipt_id)
                VALUES (:expenseRecordId, :goodsReceiptId)
                ON CONFLICT (expense_record_id) DO NOTHING
                """, params("expenseRecordId", expenseRecordId, "goodsReceiptId", event.goodsReceiptId()));
        markIntegrationProcessed(event.eventId());
    }

    @KafkaListener(topics = "procurement.supplier.payment.recorded", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeSupplierPaymentRecorded(String payload) throws Exception {
        SupplierPaymentRecordedEvent event = objectMapper.readValue(payload, SupplierPaymentRecordedEvent.class);
        if (!beginIntegrationEvent(event.eventId(), event.eventType(), payload)) {
            return;
        }
        long postingId = snowflakeIdGenerator.nextId();
        projectionJdbcTemplate.update("""
                INSERT INTO finance_projection.accounting_posting_projection (
                    posting_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, account_code, debit_amount, credit_amount, currency_code, reference_type, reference_id, payload
                ) VALUES (
                    :postingId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    NULL, NULL, 'SUPPLIER_PAYMENT', 0, :creditAmount, :currencyCode, 'SUPPLIER_PAYMENT', :referenceId, CAST(:payload AS jsonb)
                )
                ON CONFLICT (source_event_id) DO NOTHING
                """, params(
                "postingId", postingId,
                "sourceEventId", event.eventId(),
                "sourceService", event.sourceService(),
                "eventType", event.eventType(),
                "occurredAt", event.occurredAt(),
                "idempotencyKey", event.idempotencyKey(),
                "creditAmount", event.amount(),
                "currencyCode", event.currencyCode(),
                "referenceId", event.paymentId().toString(),
                "payload", payload
        ));
        projectionJdbcTemplate.update("""
                INSERT INTO finance_projection.reconciliation_snapshot (
                    snapshot_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                    region_id, outlet_id, business_date, snapshot_type, snapshot_value, snapshot_payload
                ) VALUES (
                    :snapshotId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                    NULL, NULL, :businessDate, 'SUPPLIER_PAYMENT', :snapshotValue, CAST(:snapshotPayload AS jsonb)
                )
                ON CONFLICT (source_event_id) DO NOTHING
                """, params(
                "snapshotId", snowflakeIdGenerator.nextId(),
                "sourceEventId", event.eventId(),
                "sourceService", event.sourceService(),
                "eventType", event.eventType(),
                "occurredAt", event.occurredAt(),
                "idempotencyKey", event.idempotencyKey(),
                "businessDate", LocalDate.ofInstant(event.paymentTime(), java.time.ZoneOffset.UTC),
                "snapshotValue", event.amount(),
                "snapshotPayload", payload
        ));
        markIntegrationProcessed(event.eventId());
    }

    private boolean beginIntegrationEvent(String sourceEventId, String eventType, String payload) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO finance.integration_event (
                        source_event_id, event_type, payload, status, received_at
                    ) VALUES (
                        :sourceEventId, :eventType, CAST(:payload AS jsonb), 'RECEIVED', CURRENT_TIMESTAMP
                    )
                    """, params("sourceEventId", sourceEventId, "eventType", eventType, "payload", payload));
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            return false;
        }
    }

    private void markIntegrationProcessed(String sourceEventId) {
        jdbcTemplate.update("""
                UPDATE finance.integration_event
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP, error_message = NULL
                WHERE source_event_id = :sourceEventId
                """, params("sourceEventId", sourceEventId));
    }

    private Long insertForId(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
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
}
