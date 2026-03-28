package com.fern.posservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record MenuResponse(
        Long outletId,
        LocalDate businessDate,
        List<MenuItem> items
) {
}
