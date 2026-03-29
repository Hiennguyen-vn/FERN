package com.fern.catalogservice.service;

import java.time.LocalDate;

final class EffectiveDateSupport {
    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private EffectiveDateSupport() {
    }

    static boolean overlaps(LocalDate leftFrom, LocalDate leftTo, LocalDate rightFrom, LocalDate rightTo) {
        LocalDate normalizedLeftTo = leftTo == null ? FAR_FUTURE : leftTo;
        LocalDate normalizedRightTo = rightTo == null ? FAR_FUTURE : rightTo;
        return !normalizedLeftTo.isBefore(rightFrom) && !normalizedRightTo.isBefore(leftFrom);
    }
}
