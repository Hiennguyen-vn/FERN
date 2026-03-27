package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.posservice.dto.PosCommands.OpenSessionRequest;
import com.fern.posservice.dto.PosCommands.ReconcileSessionRequest;
import com.fern.posservice.dto.PosResponses.PosSessionResponse;
import com.fern.posservice.service.PosService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
    private final PosService posService;

    public PosSessionController(PosService posService) {
        this.posService = posService;
    }

    @PostMapping
    public PosSessionResponse openSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody OpenSessionRequest request
    ) {
        return posService.openSession(principal, request);
    }

    @GetMapping("/{id}")
    public PosSessionResponse getSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posService.getSession(principal, id);
    }

    @GetMapping
    public List<PosSessionResponse> listSessions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long outletId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate businessDate
    ) {
        return posService.listSessions(principal, outletId, status, businessDate);
    }

    @PostMapping("/{id}/close")
    public PosSessionResponse closeSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return posService.closeSession(principal, id);
    }

    @PostMapping("/{id}/reconcile")
    public PosSessionResponse reconcileSession(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReconcileSessionRequest request
    ) {
        return posService.reconcileSession(principal, id, request);
    }
}
