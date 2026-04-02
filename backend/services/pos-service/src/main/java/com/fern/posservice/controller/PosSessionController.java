package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.posservice.dto.PosCommands.OpenSessionRequest;
import com.fern.posservice.dto.PosCommands.ReconcileSessionRequest;
import com.fern.posservice.dto.PosResponses.PosSessionResponse;
import com.fern.posservice.service.PosSessionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/pos-sessions")
@Tag(name = "Pos Session")
public class PosSessionController {
    private final PosSessionService posSessionService;

    public PosSessionController(PosSessionService posSessionService) {
        this.posSessionService = posSessionService;
    }

    @Operation(summary = "Create or execute Pos Session")
    @PostMapping
    public ResponseEntity<PosSessionResponse> openSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody OpenSessionRequest request
    ) {
        var result = posSessionService.openSession(principal, request);
        return ResponseEntity.ok()
                .header("X-Session-Existed", Boolean.toString(result.sessionExisted()))
                .body(result.session());
    }

    @Operation(summary = "Get Pos Session")
    @GetMapping("/{id}")
    public PosSessionResponse getSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posSessionService.getSession(principal, id);
    }

    @Operation(summary = "Get Pos Session")
    @GetMapping
    public List<PosSessionResponse> listSessions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) String terminalId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate businessDate,
            @RequestParam(required = false) Integer limit
    ) {
        return posSessionService.listSessions(principal, outletId, terminalId, status, businessDate, com.fern.platform.common.ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Create or execute Pos Session")
    @PostMapping("/{id}/close")
    public PosSessionResponse closeSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posSessionService.closeSession(principal, id);
    }

    @Operation(summary = "Create or execute Pos Session")
    @PostMapping("/{id}/reconcile")
    public PosSessionResponse reconcileSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReconcileSessionRequest request
    ) {
        return posSessionService.reconcileSession(principal, id, request);
    }
}
