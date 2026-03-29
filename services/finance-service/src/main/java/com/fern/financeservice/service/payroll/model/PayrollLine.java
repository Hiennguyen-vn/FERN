package com.fern.financeservice.service.payroll.model;

import java.math.BigDecimal;

public record PayrollLine(String lineType, String description, BigDecimal amount) {
}
