package com.fern.hrservice.controller;

import com.fern.hrservice.dto.HrResponses.ApprovedAttendanceResponse;
import com.fern.hrservice.dto.HrResponses.EffectiveContractResponse;
import com.fern.hrservice.service.HrAuthorizer;
import com.fern.hrservice.service.HrService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/hr")
public class InternalHrController {
    private final HrAuthorizer hrAuthorizer;
    private final HrService hrService;

    public InternalHrController(HrAuthorizer hrAuthorizer, HrService hrService) {
        this.hrAuthorizer = hrAuthorizer;
        this.hrService = hrService;
    }

    @GetMapping("/effective-contracts")
    public List<EffectiveContractResponse> effectiveContracts(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long regionId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        hrAuthorizer.requireInternalPermission(principal, PermissionCodes.HR_INTERNAL_READ);
        return hrService.findEffectiveContracts(regionId, startDate, endDate);
    }

    @GetMapping("/approved-attendance")
    public List<ApprovedAttendanceResponse> approvedAttendance(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long regionId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        hrAuthorizer.requireInternalPermission(principal, PermissionCodes.HR_INTERNAL_READ);
        return hrService.findApprovedAttendance(regionId, startDate, endDate);
    }

    @GetMapping("/approved-attendance/by-employee")
    public List<ApprovedAttendanceResponse> approvedAttendanceByEmployee(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam Long employeeId,
            @RequestParam LocalDate at
    ) {
        hrAuthorizer.requireInternalPermission(principal, PermissionCodes.HR_INTERNAL_READ);
        return hrService.findApprovedAttendanceByEmployee(employeeId, at);
    }
}
