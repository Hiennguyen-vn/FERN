package com.fern.platform.contracts;

import java.time.LocalDate;
import java.util.List;

public record SaleReservationRequest(
        Long outletId,
        LocalDate businessDate,
        Long sourceOrderId,
        List<SaleReservationItem> usageItems
) {
    public SaleReservationRequest {
        usageItems = usageItems == null ? List.of() : List.copyOf(usageItems);
    }
}
