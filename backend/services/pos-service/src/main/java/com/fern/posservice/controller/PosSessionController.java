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

@RestController
@RequestMapping("/pos-sessions")
public class PosSessionController {
    private final PosSessionService posSessionService;

    public PosSessionController(PosSessionService posSessionService) {
        this.posSessionService = posSessionService;
    }

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

    @GetMapping("/{id}")
    public PosSessionResponse getSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posSessionService.getSession(principal, id);
    }

    @GetMapping
    public List<PosSessionResponse> listSessions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) String terminalId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate businessDate
    ) {
        return posSessionService.listSessions(principal, outletId, terminalId, status, businessDate);
    }

    @PostMapping("/{id}/close")
    public PosSessionResponse closeSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posSessionService.closeSession(principal, id);
    }

    @PostMapping("/{id}/reconcile")
    public PosSessionResponse reconcileSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReconcileSessionRequest request
    ) {
        return posSessionService.reconcileSession(principal, id, request);
    }
}
