package com.fern.financeservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.financeservice.service.payroll.model.OutletAllocation;
import com.fern.financeservice.service.payroll.model.PayrollEmployeeComputation;
import com.fern.financeservice.service.payroll.model.PayrollLine;
import com.fern.financeservice.service.payroll.model.PayrollPeriodRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PayrollCalculationEngine {
    private final PayrollAllocationService payrollAllocationService;

    public PayrollCalculationEngine(PayrollAllocationService payrollAllocationService) {
        this.payrollAllocationService = payrollAllocationService;
    }

    public PayrollEmployeeComputation computeEmployeePayroll(
            PayrollPeriodRecord period,
            List<ApprovedAttendance> attendance,
            List<EffectiveContract> contracts,
            int scale,
            JsonNode overtimePolicy,
            JsonNode allowancePolicy,
            JsonNode deductionPolicy,
            JsonNode taxPolicy
    ) {
        if (attendance.isEmpty()) {
            EffectiveContract primary = contracts.isEmpty() ? null : selectContract(contracts, period.startDate());
            BigDecimal zeroAmount = scaled(BigDecimal.ZERO, scale);
            return new PayrollEmployeeComputation(
                    primary == null ? null : primary.contractId(),
                    null,
                    zeroAmount,
                    zeroAmount,
                    zeroAmount,
                    zeroAmount,
                    scaled(BigDecimal.ZERO, 2),
                    scaled(BigDecimal.ZERO, 2),
                    scaled(BigDecimal.ZERO, 2),
                    null,
                    List.of(
                            new PayrollLine("BASE", "Base salary", zeroAmount),
                            new PayrollLine("OVERTIME", "Overtime pay", zeroAmount),
                            new PayrollLine("ALLOWANCE", "Policy allowance", zeroAmount),
                            new PayrollLine("DEDUCTION", "Policy deduction", zeroAmount),
                            new PayrollLine("TAX", "Policy tax", zeroAmount),
                            new PayrollLine("NET", "Net pay", zeroAmount)
                    ),
                    List.of()
            );
        }
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalOvertime = BigDecimal.ZERO;
        BigDecimal totalWorkHours = BigDecimal.ZERO;
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;
        Map<Long, BigDecimal> outletHours = new LinkedHashMap<>();
        Map<Long, LocalDate> workedDays = new LinkedHashMap<>();

        for (ApprovedAttendance item : attendance) {
            EffectiveContract contract = selectContract(contracts, item.businessDate());
            BigDecimal base = basePayForDay(contract, period, item.workHours());
            BigDecimal overtime = overtimePay(contract, period, item.overtimeHours(), overtimePolicy);
            totalBase = totalBase.add(base);
            totalOvertime = totalOvertime.add(overtime);
            totalWorkHours = totalWorkHours.add(item.workHours());
            totalOvertimeHours = totalOvertimeHours.add(item.overtimeHours());
            outletHours.merge(item.outletId(), item.workHours().max(BigDecimal.ZERO), BigDecimal::add);
            workedDays.put(item.businessDate().toEpochDay(), item.businessDate());
        }

        BigDecimal allowance = BigDecimal.valueOf(workedDays.size())
                .multiply(decimalPolicyValue(allowancePolicy, "mealPerWorkDay", BigDecimal.ZERO)
                        .add(decimalPolicyValue(allowancePolicy, "transportPerWorkDay", BigDecimal.ZERO)));
        long lateCount = attendance.stream().filter(item -> "LATE".equals(item.attendanceStatus())).count();
        BigDecimal deduction = BigDecimal.valueOf(lateCount)
                .multiply(decimalPolicyValue(deductionPolicy, "latePenaltyPerCount", BigDecimal.ZERO));
        BigDecimal taxable = totalBase.add(totalOvertime).add(allowance).subtract(deduction);
        BigDecimal tax = taxable.max(BigDecimal.ZERO)
                .multiply(decimalPolicyValue(taxPolicy, "rate", BigDecimal.ZERO))
                .setScale(scale, RoundingMode.HALF_UP);
        BigDecimal gross = totalBase.add(totalOvertime).add(allowance).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(deduction).subtract(tax).setScale(scale, RoundingMode.HALF_UP);

        List<PayrollLine> lines = List.of(
                new PayrollLine("BASE", "Base salary", totalBase.setScale(scale, RoundingMode.HALF_UP)),
                new PayrollLine("OVERTIME", "Overtime pay", totalOvertime.setScale(scale, RoundingMode.HALF_UP)),
                new PayrollLine("ALLOWANCE", "Policy allowance", allowance.setScale(scale, RoundingMode.HALF_UP)),
                new PayrollLine("DEDUCTION", "Policy deduction", deduction.setScale(scale, RoundingMode.HALF_UP).negate()),
                new PayrollLine("TAX", "Policy tax", tax.setScale(scale, RoundingMode.HALF_UP).negate()),
                new PayrollLine("NET", "Net pay", net)
        );
        List<OutletAllocation> allocations = payrollAllocationService.allocate(outletHours, totalWorkHours, net, scale);
        EffectiveContract primary = selectContract(contracts, attendance.get(0).businessDate());
        return new PayrollEmployeeComputation(
                primary.contractId(),
                allocations.isEmpty() ? null : allocations.get(0).outletId(),
                gross,
                deduction.setScale(scale, RoundingMode.HALF_UP),
                tax,
                net,
                scaled(BigDecimal.valueOf(workedDays.size()), 2),
                scaled(totalWorkHours, 2),
                scaled(totalOvertimeHours, 2),
                null,
                lines,
                allocations
        );
    }

    public BigDecimal basePayForDay(EffectiveContract contract, PayrollPeriodRecord period, BigDecimal workHours) {
        return switch (contract.salaryType()) {
            // Monthly staff earn one full daily slice for each attended business day.
            // Partial-day attendance still affects work/overtime hours and allocation, but not base pay.
            case "MONTHLY" -> monthlyBasePayForAttendanceDay(contract, period);
            case "DAILY" -> contract.baseSalary();
            case "HOURLY" -> contract.baseSalary().multiply(workHours);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal monthlyBasePayForAttendanceDay(EffectiveContract contract, PayrollPeriodRecord period) {
        long activeDays = period.startDate().until(period.endDate().plusDays(1), ChronoUnit.DAYS);
        return contract.baseSalary().divide(BigDecimal.valueOf(Math.max(activeDays, 1L)), 8, RoundingMode.HALF_UP);
    }

    public BigDecimal overtimePay(EffectiveContract contract, PayrollPeriodRecord period, BigDecimal overtimeHours, JsonNode overtimePolicy) {
        if (overtimeHours.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal hourlyRate = switch (contract.salaryType()) {
            case "MONTHLY" -> {
                long activeDays = period.startDate().until(period.endDate().plusDays(1), ChronoUnit.DAYS);
                yield contract.baseSalary().divide(BigDecimal.valueOf(Math.max(activeDays, 1L) * 8L), 8, RoundingMode.HALF_UP);
            }
            case "DAILY" -> contract.baseSalary().divide(BigDecimal.valueOf(8), 8, RoundingMode.HALF_UP);
            case "HOURLY" -> contract.baseSalary();
            default -> BigDecimal.ZERO;
        };
        BigDecimal multiplier = decimalPolicyValue(overtimePolicy, "defaultMultiplier", new BigDecimal("1.5"));
        return hourlyRate.multiply(overtimeHours).multiply(multiplier);
    }

    public EffectiveContract selectContract(List<EffectiveContract> contracts, LocalDate businessDate) {
        List<EffectiveContract> sortedContracts = new ArrayList<>(contracts);
        sortedContracts.sort(Comparator.comparing(EffectiveContract::startDate));
        return sortedContracts.stream()
                .filter(contract -> !contract.startDate().isAfter(businessDate))
                .filter(contract -> contract.endDate() == null || !contract.endDate().isBefore(businessDate))
                .max(Comparator.comparing(EffectiveContract::startDate))
                .orElse(sortedContracts.get(sortedContracts.size() - 1));
    }

    private BigDecimal decimalPolicyValue(JsonNode policy, String field, BigDecimal defaultValue) {
        JsonNode value = policy.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (value.isBigDecimal()) {
            return value.decimalValue();
        }
        if (value.isNumber()) {
            return new BigDecimal(value.asText());
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        return new BigDecimal(text);
    }

    private BigDecimal scaled(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
