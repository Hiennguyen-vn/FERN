package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.financeservice.service.payroll.model.OutletAllocation;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayrollAllocationServiceTest {
    private final PayrollAllocationService payrollAllocationService = new PayrollAllocationService();

    @Test
    void shouldAllocateEntireNetPayToSingleOutlet() {
        Map<Long, BigDecimal> outletHours = new LinkedHashMap<>();
        outletHours.put(301L, new BigDecimal("8.0"));

        List<OutletAllocation> allocations = payrollAllocationService.allocate(
                outletHours,
                new BigDecimal("8.0"),
                new BigDecimal("125.55"),
                2
        );

        assertThat(allocations).containsExactly(
                new OutletAllocation(301L, new BigDecimal("8.00"), new BigDecimal("125.55"))
        );
    }

    @Test
    void shouldAllocateNetPayProportionallyAcrossMultipleOutlets() {
        Map<Long, BigDecimal> outletHours = new LinkedHashMap<>();
        outletHours.put(301L, new BigDecimal("8.0"));
        outletHours.put(302L, new BigDecimal("4.0"));

        List<OutletAllocation> allocations = payrollAllocationService.allocate(
                outletHours,
                new BigDecimal("12.0"),
                new BigDecimal("458.10"),
                2
        );

        assertThat(allocations).containsExactly(
                new OutletAllocation(301L, new BigDecimal("8.00"), new BigDecimal("305.40")),
                new OutletAllocation(302L, new BigDecimal("4.00"), new BigDecimal("152.70"))
        );
    }
}
