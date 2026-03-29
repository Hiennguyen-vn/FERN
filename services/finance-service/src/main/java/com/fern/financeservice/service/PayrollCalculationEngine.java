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
                .multiply(BigDecimal.valueOf(allowancePolicy.path("mealPerWorkDay").asDouble(0) + allowancePolicy.path("transportPerWorkDay").asDouble(0)));
        long lateCount = attendance.stream().filter(item -> "LATE".equals(item.attendanceStatus())).count();
        BigDecimal deduction = BigDecimal.valueOf(lateCount).multiply(BigDecimal.valueOf(deductionPolicy.path("latePenaltyPerCount").asDouble(0)));
        BigDecimal taxable = totalBase.add(totalOvertime).add(allowance).subtract(deduction);
        BigDecimal tax = taxable.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(taxPolicy.path("rate").asDouble(0))).setScale(scale, RoundingMode.HALF_UP);
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
                BigDecimal.valueOf(workedDays.size()).setScale(2, RoundingMode.HALF_UP),
                totalWorkHours.setScale(2, RoundingMode.HALF_UP),
                totalOvertimeHours.setScale(2, RoundingMode.HALF_UP),
                null,
                lines,
                allocations
        );
    }

    public BigDecimal basePayForDay(EffectiveContract contract, PayrollPeriodRecord period, BigDecimal workHours) {
        return switch (contract.salaryType()) {
            case "MONTHLY" -> {
                long activeDays = period.startDate().until(period.endDate().plusDays(1), ChronoUnit.DAYS);
                BigDecimal dailyRate = contract.baseSalary().divide(BigDecimal.valueOf(Math.max(activeDays, 1L)), 8, RoundingMode.HALF_UP);
                yield dailyRate;
            }
            case "DAILY" -> contract.baseSalary();
            case "HOURLY" -> contract.baseSalary().multiply(workHours);
            default -> BigDecimal.ZERO;
        };
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
        BigDecimal multiplier = BigDecimal.valueOf(overtimePolicy.path("defaultMultiplier").asDouble(1.5d));
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
}
