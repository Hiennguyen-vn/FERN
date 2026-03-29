package com.fern.financeservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class FinanceCommands {
    private FinanceCommands() {
    }

    public record CreatePayrollPeriodRequest(
            @NotNull Long regionId,
            @NotBlank String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalDate payDate,
            String note
    ) {
        @AssertTrue(message = "endDate must be on or after startDate")
        public boolean isDateRangeValid() {
            return !endDate.isBefore(startDate);
        }

        @AssertTrue(message = "payDate must be on or after endDate")
        public boolean isPayDateValid() {
            return payDate == null || !payDate.isBefore(endDate);
        }
    }

    public record CreatePayrollRunRequest(
            @NotNull Long payrollPeriodId,
            LocalDate runDate,
            String note
    ) {
    }

    public record ReviewPayrollRequest(
            String note
    ) {
    }

    public record MarkPaidRequest(
            @NotBlank String paymentReference,
            String note
    ) {
    }

    public record PutNumberingRuleRequest(
            String prefix,
            Long regionId,
            Long outletId,
            Long nextNumber,
            String resetPeriod,
            String formatPattern,
            Boolean active
    ) {
    }

    public record PutSystemPolicyRequest(
            @NotNull JsonNode policyValue,
            String description
    ) {
    }
}
