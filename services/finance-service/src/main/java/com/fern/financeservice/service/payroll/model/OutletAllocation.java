package com.fern.financeservice.service.payroll.model;

import java.math.BigDecimal;

public record OutletAllocation(Long outletId, BigDecimal workHours, BigDecimal allocatedAmount) {
}
