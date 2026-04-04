package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.posservice.dto.PosCommands.CreateTableRequest;
import com.fern.posservice.dto.PosCommands.UpdateTableRequest;
import com.fern.posservice.dto.PosResponses.DiningTableResponse;
import com.fern.posservice.service.PosDineInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos/tables")
@Tag(name = "POS — Dine-In Tables")
public class DineInController {
    private final PosDineInService dineInService;

    public DineInController(PosDineInService dineInService) {
        this.dineInService = dineInService;
    }

    @Operation(summary = "Create a dining table")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiningTableResponse createTable(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateTableRequest request
    ) {
        return dineInService.createTable(principal, request);
    }

    @Operation(summary = "Update a dining table")
    @PutMapping("/{id}")
    public DiningTableResponse updateTable(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableRequest request
    ) {
        return dineInService.updateTable(principal, id, request);
    }

    @Operation(summary = "Get a dining table by ID")
    @GetMapping("/{id}")
    public DiningTableResponse getTable(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return dineInService.getTableById(principal, id);
    }

    @Operation(summary = "List dining tables for an outlet")
    @GetMapping
    public List<DiningTableResponse> listTables(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId
    ) {
        return dineInService.listTables(principal, outletId);
    }

    @Operation(summary = "Update table status (e.g., AVAILABLE, RESERVED, CLEANING)")
    @PostMapping("/{id}/status")
    public DiningTableResponse updateTableStatus(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestParam String status
    ) {
        return dineInService.updateTableStatus(principal, id, status);
    }
}
