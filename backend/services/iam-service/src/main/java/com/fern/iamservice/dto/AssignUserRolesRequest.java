package com.fern.iamservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignUserRolesRequest(@NotNull List<String> roleCodes) {
}
