package com.fern.posservice.service;

import java.math.BigDecimal;

record PricedLine(
        Integer lineNumber,
        Long productId,
        String productCode,
        String productNameSnapshot,
        BigDecimal unitPrice,
        BigDecimal qty,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal,
        String note
) {
}
