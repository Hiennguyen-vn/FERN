package com.fern.hrservice.controller;

import com.fern.hrservice.dto.HrCommands.CreateAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateContractRequest;
import com.fern.hrservice.dto.HrCommands.CreateEmployeeRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftScheduleRequest;
import com.fern.hrservice.dto.HrCommands.RecordAttendanceEventRequest;
import com.fern.hrservice.dto.HrCommands.ReviewAttendanceRequest;
import com.fern.hrservice.dto.HrResponses.AssignmentResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceApprovalResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceEventResponse;
import com.fern.hrservice.dto.HrResponses.ContractResponse;
import com.fern.hrservice.dto.HrResponses.EmployeeResponse;
import com.fern.hrservice.dto.HrResponses.ShiftAssignmentResponse;
import com.fern.hrservice.dto.HrResponses.ShiftScheduleResponse;
import com.fern.hrservice.service.HrService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.observability.CorrelationId;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HrCommandController {
    private final HrService hrService;

    public HrCommandController(HrService hrService) {
        this.hrService = hrService;
    }

    @PostMapping("/employees")
    public EmployeeResponse createEmployee(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateEmployeeRequest request
    ) {
        return hrService.createEmployee(principal, request);
    }

    @PostMapping("/employee-contracts")
    public ContractResponse createContract(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateContractRequest request
    ) {
        return hrService.createContract(principal, request);
    }

    @PostMapping("/employee-assignments")
    public AssignmentResponse createAssignment(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        return hrService.createAssignment(principal, request);
    }

    @PostMapping("/shift-schedules")
    public ShiftScheduleResponse createShiftSchedule(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateShiftScheduleRequest request
    ) {
        return hrService.createShiftSchedule(principal, request);
    }

    @PostMapping("/shift-assignments")
    public ShiftAssignmentResponse createShiftAssignment(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateShiftAssignmentRequest request
    ) {
        return hrService.createShiftAssignment(principal, request);
    }

    @PostMapping("/attendance-events")
    public AttendanceEventResponse recordAttendanceEvent(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody RecordAttendanceEventRequest request
    ) {
        return hrService.recordAttendanceEvent(principal, request);
    }

    @PostMapping("/attendance-approvals/{shiftAssignmentId}/approve")
    public AttendanceApprovalResponse approveAttendance(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long shiftAssignmentId,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @RequestBody(required = false) ReviewAttendanceRequest request
    ) {
        return hrService.reviewAttendance(principal, shiftAssignmentId, "APPROVED", request == null ? null : request.comments(), correlationId);
    }

    @PostMapping("/attendance-approvals/{shiftAssignmentId}/reject")
    public AttendanceApprovalResponse rejectAttendance(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long shiftAssignmentId,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationId,
            @RequestBody(required = false) ReviewAttendanceRequest request
    ) {
        return hrService.reviewAttendance(principal, shiftAssignmentId, "REJECTED", request == null ? null : request.comments(), correlationId);
    }
}
