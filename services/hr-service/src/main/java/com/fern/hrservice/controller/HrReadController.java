package com.fern.hrservice.controller;

import com.fern.hrservice.dto.HrResponses.AssignmentResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceApprovalResponse;
import com.fern.hrservice.dto.HrResponses.ContractResponse;
import com.fern.hrservice.dto.HrResponses.EmployeeResponse;
import com.fern.hrservice.dto.HrResponses.ShiftAssignmentResponse;
import com.fern.hrservice.dto.HrResponses.ShiftScheduleResponse;
import com.fern.hrservice.service.ContractResponseMasker;
import com.fern.hrservice.service.HrService;
import com.fern.platform.common.FernPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HrReadController {
    private final HrService hrService;
    private final ContractResponseMasker contractResponseMasker;

    public HrReadController(HrService hrService, ContractResponseMasker contractResponseMasker) {
        this.hrService = hrService;
        this.contractResponseMasker = contractResponseMasker;
    }

    @GetMapping("/employees/{id}")
    public EmployeeResponse getEmployee(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return hrService.getEmployee(principal, id);
    }

    @GetMapping("/employees/{employeeId}/contracts")
    public List<ContractResponse> listContracts(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long employeeId) {
        return hrService.listContracts(principal, employeeId).stream()
                .map(response -> contractResponseMasker.maskForPrincipal(principal, response))
                .toList();
    }

    @GetMapping("/employees/{employeeId}/assignments")
    public List<AssignmentResponse> listAssignments(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long employeeId) {
        return hrService.listAssignments(principal, employeeId);
    }

    @GetMapping("/shift-schedules/{id}")
    public ShiftScheduleResponse getShiftSchedule(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return hrService.getShiftSchedule(principal, id);
    }

    @GetMapping("/shift-assignments/{id}")
    public ShiftAssignmentResponse getShiftAssignment(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        return hrService.getShiftAssignment(principal, id);
    }

    @GetMapping("/attendance-approvals/{shiftAssignmentId}")
    public AttendanceApprovalResponse getAttendanceApproval(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long shiftAssignmentId
    ) {
        return hrService.getAttendanceApproval(principal, shiftAssignmentId);
    }

    @GetMapping("/attendance-approvals")
    public List<AttendanceApprovalResponse> listAttendanceApprovals(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long outletId
    ) {
        return hrService.listAttendanceApprovals(principal, regionId, outletId);
    }
}
