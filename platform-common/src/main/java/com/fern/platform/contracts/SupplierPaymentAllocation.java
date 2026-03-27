package com.fern.platform.contracts;

import java.math.BigDecimal;

public record SupplierPaymentAllocation(
        Long supplierInvoiceId,
        BigDecimal allocatedAmount
) {
}
