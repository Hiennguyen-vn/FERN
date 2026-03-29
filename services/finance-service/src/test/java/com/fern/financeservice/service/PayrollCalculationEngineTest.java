package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.financeservice.service.payroll.model.PayrollEmployeeComputation;
import com.fern.financeservice.service.payroll.model.PayrollPeriodRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayrollCalculationEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayrollCalculationEngine payrollCalculationEngine = new PayrollCalculationEngine(new PayrollAllocationService());

    @Test
    void shouldComputeMonthlyPayrollWithOvertimeAllowanceDeductionTaxAndAllocations() throws Exception {
        PayrollPeriodRecord period = new PayrollPeriodRecord(1L, 10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 7), "DRAFT");
        EffectiveContract contract = new EffectiveContract(701L, 501L, 10L, "FULL_TIME", "MONTHLY", new BigDecimal("1000.00"), "TAX-001", LocalDate.of(2026, 1, 1), null);
        List<ApprovedAttendance> attendance = List.of(
                new ApprovedAttendance(11L, 21L, 501L, 10L, 301L, 701L, LocalDate.of(2026, 3, 1), "PRESENT", new BigDecimal("8.0"), new BigDecimal("2.0")),
                new ApprovedAttendance(12L, 22L, 501L, 10L, 302L, 701L, LocalDate.of(2026, 3, 2), "LATE", new BigDecimal("4.0"), BigDecimal.ZERO)
        );

        PayrollEmployeeComputation computation = payrollCalculationEngine.computeEmployeePayroll(
                period,
                attendance,
                List.of(contract),
                2,
                json("{\"defaultMultiplier\":2.0}"),
                json("{\"mealPerWorkDay\":5.0,\"transportPerWorkDay\":3.0}"),
                json("{\"latePenaltyPerCount\":7.0}"),
                json("{\"rate\":0.1}")
        );

        assertThat(computation.primaryContractId()).isEqualTo(701L);
        assertThat(computation.primaryOutletId()).isEqualTo(301L);
        assertThat(computation.grossPay()).isEqualByComparingTo("516.00");
        assertThat(computation.deductionAmount()).isEqualByComparingTo("7.00");
        assertThat(computation.taxAmount()).isEqualByComparingTo("50.90");
        assertThat(computation.netPay()).isEqualByComparingTo("458.10");
        assertThat(computation.workDays()).isEqualByComparingTo("2.00");
        assertThat(computation.workHours()).isEqualByComparingTo("12.00");
        assertThat(computation.overtimeHours()).isEqualByComparingTo("2.00");
        assertThat(computation.lines()).extracting(line -> line.lineType() + ":" + line.amount())
                .containsExactly(
                        "BASE:400.00",
                        "OVERTIME:100.00",
                        "ALLOWANCE:16.00",
                        "DEDUCTION:-7.00",
                        "TAX:-50.90",
                        "NET:458.10"
                );
        assertThat(computation.allocations()).extracting(allocation -> allocation.outletId() + ":" + allocation.allocatedAmount())
                .containsExactly("301:305.40", "302:152.70");
    }

    @Test
    void shouldComputeHourlyPayrollUsingWorkedHoursAndOvertimeMultiplier() throws Exception {
        PayrollPeriodRecord period = new PayrollPeriodRecord(1L, 10L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "DRAFT");
        EffectiveContract contract = new EffectiveContract(702L, 502L, 10L, "PART_TIME", "HOURLY", new BigDecimal("15.00"), "TAX-002", LocalDate.of(2026, 3, 1), null);
        List<ApprovedAttendance> attendance = List.of(
                new ApprovedAttendance(13L, 23L, 502L, 10L, 401L, 702L, LocalDate.of(2026, 3, 10), "PRESENT", new BigDecimal("6.0"), new BigDecimal("1.0"))
        );

        PayrollEmployeeComputation computation = payrollCalculationEngine.computeEmployeePayroll(
                period,
                attendance,
                List.of(contract),
                2,
                json("{\"defaultMultiplier\":1.5}"),
                json("{}"),
                json("{}"),
                json("{\"rate\":0}")
        );

        assertThat(computation.grossPay()).isEqualByComparingTo("112.50");
        assertThat(computation.netPay()).isEqualByComparingTo("112.50");
        assertThat(computation.workHours()).isEqualByComparingTo("6.00");
        assertThat(computation.overtimeHours()).isEqualByComparingTo("1.00");
        assertThat(computation.allocations()).hasSize(1);
        assertThat(computation.allocations().getFirst().allocatedAmount()).isEqualByComparingTo("112.50");
    }

    @Test
    void shouldSelectContractByBusinessDate() {
        List<EffectiveContract> contracts = List.of(
                new EffectiveContract(701L, 501L, 10L, "FULL_TIME", "DAILY", new BigDecimal("100.00"), "TAX-001", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15)),
                new EffectiveContract(702L, 501L, 10L, "FULL_TIME", "DAILY", new BigDecimal("200.00"), "TAX-001", LocalDate.of(2026, 3, 16), null)
        );

        assertThat(payrollCalculationEngine.selectContract(contracts, LocalDate.of(2026, 3, 10)).contractId()).isEqualTo(701L);
        assertThat(payrollCalculationEngine.selectContract(contracts, LocalDate.of(2026, 3, 20)).contractId()).isEqualTo(702L);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
