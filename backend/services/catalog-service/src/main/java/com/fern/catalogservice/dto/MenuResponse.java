package com.fern.catalogservice.dto;

import java.time.LocalDate;
import java.util.List;

public record MenuResponse(
        Long outletId,
        LocalDate businessDate,
        List<MenuItemResponse> items
) {
}
