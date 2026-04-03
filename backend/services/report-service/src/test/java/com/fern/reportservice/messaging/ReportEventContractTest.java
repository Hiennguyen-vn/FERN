package com.fern.reportservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.InventoryAdjustmentPostedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.StockCountPostedEvent;
import com.fern.platform.contracts.WasteRecordPostedEvent;
import com.fern.platform.testsupport.KafkaContractFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Consumer-side contract tests for the report event consumer.
 *
 * <p>Validates that each event type can be deserialized from a "known-good"
 * JSON payload — the exact format that the producing service emits.
 * If a producer renames a field or changes a type, this test fails.
 */
class ReportEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static java.util.stream.Stream<Arguments> eventPayloads() {
        return java.util.stream.Stream.of(
                Arguments.of("PosSaleCompletedEvent", KafkaContractFixtures.load("pos.sale.completed.json"), PosSaleCompletedEvent.class),
                Arguments.of("ProcurementGoodsReceiptPostedEvent", KafkaContractFixtures.load("procurement.goods_receipt.posted.json"), ProcurementGoodsReceiptPostedEvent.class),
                Arguments.of("AttendanceApprovedEvent", """
                        {
                          "eventId": "att-1", "eventType": "attendance.approved",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "hr-service",
                          "correlationId": "corr-3", "idempotencyKey": "attendance.approved:1",
                          "approvalId": 1, "shiftAssignmentId": 20, "employeeId": 10,
                          "regionId": 1, "outletId": 101, "businessDate": "2026-03-29",
                          "attendanceStatus": "PRESENT", "workHours": 8.0, "overtimeHours": 1.5,
                          "contractId": 30, "approvedByUserId": 5
                        }
                        """, AttendanceApprovedEvent.class),
                Arguments.of("PayrollCalculatedEvent", """
                        {
                          "eventId": "payroll-1", "eventType": "payroll.calculated",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "finance-service",
                          "correlationId": "corr-4", "idempotencyKey": "payroll.calculated:run:1",
                          "regionId": 1, "payrollPeriodId": 2, "payrollRunId": 3,
                          "businessDate": "2026-03-29", "totalAmount": 50000.00, "approvedByUserId": 7,
                          "employees": [], "allocations": []
                        }
                        """, PayrollCalculatedEvent.class),
                Arguments.of("PayrollPostedEvent", """
                        {
                          "eventId": "payroll-posted-1", "eventType": "payroll.posted",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "finance-service",
                          "correlationId": "corr-5", "idempotencyKey": "payroll.posted:run:1",
                          "regionId": 1, "payrollPeriodId": 2, "payrollRunId": 3,
                          "businessDate": "2026-03-29", "totalAmount": 50000.00,
                          "paymentReference": "BANK-REF-123", "markedPaidByUserId": 7, "expenses": []
                        }
                        """, PayrollPostedEvent.class),
                Arguments.of("ExpensePostedEvent", """
                        {
                          "eventId": "expense-1", "eventType": "finance.expense.posted",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "finance-service",
                          "correlationId": "corr-6", "idempotencyKey": "finance.expense.posted:expense:1",
                          "expenseRecordId": 10, "regionId": 1, "outletId": 101,
                          "employeeId": 55, "payrollRunId": 77, "businessDate": "2026-03-29",
                          "sourceType": "PAYROLL", "amount": 42.25,
                          "sourceReferenceType": "PAYROLL_RUN", "sourceReferenceId": "77"
                        }
                        """, ExpensePostedEvent.class),
                Arguments.of("InventoryAdjustmentPostedEvent", """
                        {
                          "eventId": "adj-1", "eventType": "inventory.adjustment.posted",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "inventory-service",
                          "correlationId": "corr-7", "idempotencyKey": "inventory.adjustment.posted:adj:1",
                          "stockAdjustmentId": 1, "regionId": 1, "outletId": 101, "ingredientId": 200,
                          "businessDate": "2026-03-29", "postedAt": "2026-03-29T10:00:00Z", "postedByUserId": 7,
                          "adjustmentDirection": "IN", "reason": "STOCK_ADJUSTMENT",
                          "qtyChange": 5.00, "unitCost": 10.50,
                          "sourceReferenceType": "STOCK_ADJUSTMENT", "sourceReferenceId": "100"
                        }
                        """, InventoryAdjustmentPostedEvent.class),
                Arguments.of("WasteRecordPostedEvent", """
                        {
                          "eventId": "waste-1", "eventType": "inventory.waste.posted",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "inventory-service",
                          "correlationId": "corr-8", "idempotencyKey": "inventory.waste.posted:waste:1",
                          "wasteRecordId": 1, "regionId": 1, "outletId": 101, "ingredientId": 200,
                          "businessDate": "2026-03-29", "postedAt": "2026-03-29T10:00:00Z", "postedByUserId": 7,
                          "reason": "expired", "qtyChange": -2.00, "unitCost": 10.50,
                          "sourceReferenceType": "WASTE_RECORD", "sourceReferenceId": "100"
                        }
                        """, WasteRecordPostedEvent.class),
                Arguments.of("StockCountPostedEvent", """
                        {
                          "eventId": "count-1", "eventType": "inventory.stock_count.posted",
                          "occurredAt": "2026-03-29T10:00:00Z", "sourceService": "inventory-service",
                          "correlationId": "corr-9", "idempotencyKey": "inventory.stock_count.posted:session:1",
                          "stockCountSessionId": 500, "regionId": 1, "outletId": 101,
                          "businessDate": "2026-03-29", "postedAt": "2026-03-29T10:00:00Z", "postedByUserId": 7,
                          "lines": [{"ingredientId": 200, "systemQty": 10.00, "actualQty": 9.00, "varianceQty": -1.00, "unitCost": 5.00}]
                        }
                        """, StockCountPostedEvent.class)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventPayloads")
    void shouldDeserializeKnownPayload(String name, String json, Class<?> type) {
        assertThatNoException().isThrownBy(() -> {
            Object event = objectMapper.readValue(json, type);
            assertThat(event).isNotNull();
        });
    }

    @Test
    void shouldDeserializeSaleEventWithFullPayloadIntegrity() throws Exception {
        PosSaleCompletedEvent event = objectMapper.readValue(
                KafkaContractFixtures.load("pos.sale.completed.json"),
                PosSaleCompletedEvent.class
        );

        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.saleOrderId()).isEqualTo(100L);
        assertThat(event.regionId()).isEqualTo(1L);
        assertThat(event.outletId()).isEqualTo(101L);
        assertThat(event.payments()).hasSize(2);
        assertThat(event.payments().getFirst().paymentMethod()).isEqualTo("CASH");
        assertThat(event.payments().getFirst().amount()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(event.reservationId()).isEqualTo(999L);
    }
}
