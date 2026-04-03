package com.fern.platform.contracts;

import java.math.BigDecimal;

public record SupplierInvoiceApprovedLine(
        Long lineId,
        Integer lineNumber,
        String lineType,
        Long goodsReceiptLineId,
        String description,
        BigDecimal qtyInvoiced,
        BigDecimal unitPrice,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {
}
