package com.fern.platform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventVersionCompatibilityTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDefaultMissingEventVersionToOneWhenDeserializingLegacyPayload() throws Exception {
        ExpensePostedEvent expense = objectMapper.readValue("""
                {
                  "eventId": "expense-1",
                  "eventType": "finance.expense.posted",
                  "occurredAt": "2026-03-29T10:15:30Z",
                  "sourceService": "finance-service",
                  "correlationId": "corr-expense-1",
                  "idempotencyKey": "finance.expense.posted:expense:10",
                  "expenseRecordId": 10,
                  "regionId": 1,
                  "outletId": 101,
                  "employeeId": 55,
                  "payrollRunId": 77,
                  "businessDate": "2026-03-29",
                  "sourceType": "PAYROLL",
                  "amount": 42.25,
                  "sourceReferenceType": "PAYROLL_RUN",
                  "sourceReferenceId": "77"
                }
                """, ExpensePostedEvent.class);
        PosSaleCompletedEvent sale = objectMapper.readValue("""
                {
                  "eventId": "sale-1",
                  "eventType": "pos.sale.completed",
                  "occurredAt": "2026-03-29T10:15:30Z",
                  "sourceService": "pos-service",
                  "correlationId": "corr-sale-1",
                  "idempotencyKey": "pos.sale.completed:sale:10",
                  "saleOrderId": 10,
                  "sessionId": 20,
                  "regionId": 1,
                  "outletId": 101,
                  "businessDate": "2026-03-29",
                  "completedAt": "2026-03-29T10:15:30Z",
                  "completedByUserId": 5,
                  "reservationId": 99,
                  "payments": [],
                  "saleSnapshot": {"lines":[]},
                  "recipeUsageItems": []
                }
                """, PosSaleCompletedEvent.class);

        assertThat(expense.eventVersion()).isEqualTo(1);
        assertThat(sale.eventVersion()).isEqualTo(1);
    }

    @Test
    void shouldSerializeCurrentVersionForLegacyConstructors() throws Exception {
        List<Object> events = List.of(
                new AttendanceApprovedEvent("attendance-1", "attendance.approved", instant(), "hr-service", "corr-1", "attendance.approved:1",
                        1L, 2L, 3L, 4L, 5L, date(), "APPROVED", decimal("8"), decimal("1"), 6L, 7L),
                new ExpensePostedEvent("expense-1", "finance.expense.posted", instant(), "finance-service", "corr-2", "finance.expense.posted:expense:1",
                        1L, 2L, 3L, 4L, 5L, date(), "PAYROLL", decimal("12.50"), "PAYROLL_RUN", "5"),
                new InventoryAdjustmentPostedEvent("adjustment-1", "inventory.adjustment.posted", instant(), "inventory-service", "corr-3", "inventory.adjustment.posted:adjustment:1",
                        1L, 2L, 3L, 4L, date(), instant(), 9L, "IN", "RESET", decimal("2"), decimal("3.5"), "STOCK_ADJUSTMENT", "1"),
                new OperationalAlertEvent("alert-1", "operational.alert", instant(), "platform", "corr-4", "alert:1",
                        "EXPORT_FAILED", "HIGH", "failed", 1L, 101L, "EXPORT_JOB", "1", Map.of("jobId", 1)),
                new PayrollCalculatedEvent("payroll-calculated-1", "payroll.calculated", instant(), "finance-service", "corr-5", "payroll.calculated:run:1",
                        1L, 2L, 3L, date(), decimal("50"), 7L,
                        List.of(new PayrollCalculatedEvent.PayrollCalculatedEmployee(11L, 101L, decimal("20"), decimal("1"), decimal("2"), decimal("17"))),
                        List.of(new PayrollCalculatedEvent.PayrollAllocation(11L, 101L, decimal("8"), decimal("17")))),
                new PayrollPostedEvent("payroll-posted-1", "payroll.posted", instant(), "finance-service", "corr-6", "payroll.posted:run:1",
                        1L, 2L, 3L, date(), decimal("50"), "PAYREF", 7L,
                        List.of(new PayrollPostedEvent.PayrollExpenseLink(99L, 11L, 101L, decimal("50")))),
                new PosSaleCompletedEvent("sale-1", "pos.sale.completed", instant(), "pos-service", "corr-7", "pos.sale.completed:sale:1",
                        1L, 2L, 3L, 4L, date(), instant(), 7L, 8L,
                        List.of(new SalePaymentSnapshot(9L, "CASH", decimal("25"), "CAPTURED", instant(), "txn-1")),
                        Map.of("lines", List.of()), List.of()),
                new ProcurementGoodsReceiptPostedEvent("receipt-1", "procurement.goods_receipt.posted", instant(), "procurement-service", "corr-8", "procurement.goods_receipt.posted:receipt:1",
                        1L, 2L, 3L, 4L, date(), instant(), 7L,
                        List.of(new GoodsReceiptPostedLine(88L, decimal("2"), decimal("3"), 5L))),
                new StockCountPostedEvent("count-1", "inventory.stock_count.posted", instant(), "inventory-service", "corr-9", "inventory.stock_count.posted:session:1",
                        1L, 2L, 3L, date(), instant(), 7L,
                        List.of(new StockCountPostedLine(88L, decimal("10"), decimal("11"), decimal("1"), decimal("3")))),
                new SupplierPaymentRecordedEvent("payment-1", "procurement.supplier.payment.recorded", instant(), "procurement-service", "corr-10", "procurement.supplier.payment.recorded:payment:1",
                        1L, 2L, instant(), decimal("10"), "USD",
                        List.of(new SupplierPaymentAllocation(77L, decimal("10"))), 9L),
                new WasteRecordPostedEvent("waste-1", "inventory.waste.posted", instant(), "inventory-service", "corr-11", "inventory.waste.posted:waste:1",
                        1L, 2L, 3L, 4L, date(), instant(), 7L, "expired", decimal("-1"), decimal("2"), "WASTE_RECORD", "1")
        );

        for (Object event : events) {
            JsonNode node = objectMapper.valueToTree(event);
            assertThat(node.path("eventVersion").intValue())
                    .describedAs("eventVersion missing for %s", event.getClass().getSimpleName())
                    .isEqualTo(1);
        }
    }

    private Instant instant() {
        return Instant.parse("2026-03-29T10:15:30Z");
    }

    private LocalDate date() {
        return LocalDate.parse("2026-03-29");
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
