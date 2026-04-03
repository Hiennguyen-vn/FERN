package com.fern.platform.web;

import java.util.Objects;

public record FernDownstreamClientSpec(
        String callerService,
        String targetService,
        String operation,
        FernDownstreamClientProperties properties
) {
    public FernDownstreamClientSpec {
        Objects.requireNonNull(callerService, "callerService");
        Objects.requireNonNull(targetService, "targetService");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(properties, "properties");
    }
}
