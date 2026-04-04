package com.fern.posservice.service;

import java.math.BigDecimal;
import java.util.List;

record PricingSnapshot(
        List<PricedLine> lines,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String promotionCode
) {
}
