package com.fern.iamservice.dto;

import jakarta.validation.Valid;
import java.util.List;

public record PutUserPermissionOverridesRequest(
        @Valid List<PermissionOverrideItemRequest> overrides
) {
}
