package com.fern.platform.contracts;

import java.time.Instant;

public record SaleReservationResponse(
        Long reservationId,
        Instant expiresAt
) {
}
