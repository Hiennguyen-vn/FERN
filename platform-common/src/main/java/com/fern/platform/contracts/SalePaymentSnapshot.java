package com.fern.platform.contracts;

import java.math.BigDecimal;
import java.time.Instant;

public record SalePaymentSnapshot(
        Long paymentId,
        String paymentMethod,
        BigDecimal amount,
        String status,
        Instant paymentTime,
        String transactionRef
) {
}
