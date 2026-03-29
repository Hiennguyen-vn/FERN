package com.fern.posservice.service;

import com.fern.posservice.dto.PosResponses.PosSessionResponse;

public record PosSessionOpenResult(
        PosSessionResponse session,
        boolean sessionExisted
) {
}
