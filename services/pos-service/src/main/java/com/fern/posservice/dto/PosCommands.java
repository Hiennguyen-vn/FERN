package com.fern.posservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PosCommands {
    private PosCommands() {
    }

    public record OpenSessionRequest(
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull String currencyCode,
            @NotNull LocalDate businessDate,
            String note
    ) {
    }

    public record ReconcileSessionRequest(
            @NotNull @DecimalMin(value = "0.00") BigDecimal countedCashAmount,
            String note
    ) {
    }

    public record OrderLineInput(
            @NotNull Long productId,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal qty,
            String note
    ) {
    }

    public record CreateSaleOrderRequest(
            @NotNull Long posSessionId,
            @NotNull String orderType,
            String note,
            @NotEmpty List<@Valid OrderLineInput> lines
    ) {
    }

    public record UpdateSaleOrderRequest(
            String note,
            @NotEmpty List<@Valid OrderLineInput> lines
    ) {
    }

    public record AddPaymentRequest(
            @NotNull String paymentMethod,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            Instant paymentTime,
            String transactionRef,
            String note,
            String status
    ) {
    }
}
