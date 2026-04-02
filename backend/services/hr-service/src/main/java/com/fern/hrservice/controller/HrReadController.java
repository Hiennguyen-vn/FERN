package com.fern.hrservice.controller;

import com.fern.hrservice.dto.HrResponses.AssignmentResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceApprovalResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceEventListItemResponse;
import com.fern.hrservice.dto.HrResponses.ContractResponse;
import com.fern.hrservice.dto.HrResponses.EmployeeResponse;
import com.fern.hrservice.dto.HrResponses.ShiftAssignmentResponse;
import com.fern.hrservice.dto.HrResponses.ShiftScheduleResponse;
import com.fern.hrservice.service.ContractResponseMasker;
import com.fern.hrservice.service.HrService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping
@Tag(name = "HR — Queries")
public class HrReadController {
    private final HrService hrService;
    private final ContractResponseMasker contractResponseMasker;

    public HrReadController(HrService hrService, ContractResponseMasker contractResponseMasker) {
        this.hrService = hrService;
        this.contractResponseMasker = contractResponseMasker;
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/employees/{id}")
    public EmployeeResponse getEmployee(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return hrService.getEmployee(principal, id);
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/employees")
    public PageResponse<EmployeeResponse> listEmployees(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        return hrService.listEmployees(principal, search, status, page, size);
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/employees/{employeeId}/contracts")
    public List<ContractResponse> listContracts(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long employeeId) {
        return hrService.listContracts(principal, employeeId).stream()
                .map(response -> contractResponseMasker.maskForPrincipal(principal, response))
                .toList();
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/employee-contracts")
    public PageResponse<ContractResponse> browseContracts(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size
    ) {
        PageResponse<ContractResponse> response = hrService.listContracts(principal, employeeId, regionId, search, status, page, size);
        return new PageResponse<>(
                response.items().stream()
                        .map(item -> contractResponseMasker.maskForPrincipal(principal, item))
                        .toList(),
                response.page(),
                response.size(),
                response.hasMore()
        );
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/employees/{employeeId}/assignments")
    public List<AssignmentResponse> listAssignments(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long employeeId) {
        return hrService.listAssignments(principal, employeeId);
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/shift-schedules/{id}")
    public ShiftScheduleResponse getShiftSchedule(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return hrService.getShiftSchedule(principal, id);
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/shift-schedules")
    public List<ShiftScheduleResponse> listShiftSchedules(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) java.time.LocalDate fromDate,
            @RequestParam(required = false) java.time.LocalDate toDate,
            @RequestParam(required = false) Integer limit
    ) {
        return hrService.listShiftSchedules(principal, outletId, fromDate, toDate,
                com.fern.platform.common.ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/shift-assignments/{id}")
    public ShiftAssignmentResponse getShiftAssignment(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return hrService.getShiftAssignment(principal, id);
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/shift-assignments")
    public List<ShiftAssignmentResponse> listShiftAssignments(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long shiftScheduleId,
            @RequestParam(required = false) Integer limit
    ) {
        return hrService.listShiftAssignments(principal, shiftScheduleId,
                com.fern.platform.common.ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/attendance-approvals/{shiftAssignmentId}")
    public AttendanceApprovalResponse getAttendanceApproval(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long shiftAssignmentId
    ) {
        return hrService.getAttendanceApproval(principal, shiftAssignmentId);
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/attendance-approvals")
    public List<AttendanceApprovalResponse> listAttendanceApprovals(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) Integer limit
    ) {
        return hrService.listAttendanceApprovals(principal, regionId, outletId, com.fern.platform.common.ListQueryDefaults.clampLimit(limit));
    }

    @Operation(summary = "Get HR — Queries")
    @GetMapping("/attendance-events")
    public PageResponse<AttendanceEventListItemResponse> listAttendanceEvents(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long shiftAssignmentId,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long outletId,
            @RequestParam(required = false) java.time.LocalDate fromDate,
            @RequestParam(required = false) java.time.LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "desc") String sort
    ) {
        return hrService.listAttendanceEvents(principal, employeeId, shiftAssignmentId, regionId, outletId, fromDate, toDate, page, size, sort);
    }
}
