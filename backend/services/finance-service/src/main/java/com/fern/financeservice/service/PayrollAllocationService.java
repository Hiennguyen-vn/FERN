package com.fern.financeservice.service;

import com.fern.financeservice.service.payroll.model.OutletAllocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PayrollAllocationService {
    public List<OutletAllocation> allocate(Map<Long, BigDecimal> outletHours, BigDecimal totalWorkHours, BigDecimal netPay, int scale) {
        List<OutletAllocation> allocations = new ArrayList<>();
        BigDecimal denominator = totalWorkHours.compareTo(BigDecimal.ZERO) > 0 ? totalWorkHours : BigDecimal.ONE;
        for (Map.Entry<Long, BigDecimal> entry : outletHours.entrySet()) {
            BigDecimal ratio = entry.getValue().divide(denominator, 8, RoundingMode.HALF_UP);
            allocations.add(new OutletAllocation(
                    entry.getKey(),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    netPay.multiply(ratio).setScale(scale, RoundingMode.HALF_UP)
            ));
        }
        return allocations;
    }
}
