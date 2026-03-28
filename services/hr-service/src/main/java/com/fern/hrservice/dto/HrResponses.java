package com.fern.hrservice.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class HrResponses {
    private HrResponses() {
    }

    public record EmployeeResponse(
            Long id,
            String employeeCode,
            String fullName,
            LocalDate dob,
            String gender,
            String email,
            String phone,
            String status,
            LocalDate hiredAt,
            Long userAccountId
    ) {
    }

    public record ContractResponse(
            Long id,
            Long employeeId,
            String employmentType,
            String salaryType,
            BigDecimal baseSalary,
            Long regionId,
            String taxCode,
            String contractStatus,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record AssignmentResponse(
            Long id,
            Long employeeId,
            Long regionId,
            Long outletId,
            String positionTitle,
            LocalDate startDate,
            LocalDate endDate,
            boolean primaryAssignment,
            String status
    ) {
    }

    public record ShiftScheduleResponse(
            Long id,
            Long regionId,
            Long outletId,
            LocalDate shiftDate,
            String shiftName,
            LocalTime startTime,
            LocalTime endTime,
            String status
    ) {
    }

    public record ShiftAssignmentResponse(
            Long id,
            Long shiftScheduleId,
            Long employeeId,
            String assignedRole,
            String attendanceStatus,
            String approvalStatus,
            String note
    ) {
    }

    public record AttendanceEventResponse(
            Long id,
            Long employeeId,
            Long shiftAssignmentId,
            String eventType,
            Instant eventTime,
            String sourceSystem
    ) {
    }

    public record AttendanceEventListItemResponse(
            Long id,
            Long employeeId,
            Long regionId,
            Long outletId,
            Long shiftAssignmentId,
            LocalDate shiftDate,
            String eventType,
            Instant eventTime,
            String sourceSystem
    ) {
    }

    public record AttendanceApprovalResponse(
            Long id,
            Long shiftAssignmentId,
            String status,
            String comments,
            Instant approvedAt,
            Long approvedByUserId,
            String attendanceStatus,
            BigDecimal workHours,
            BigDecimal overtimeHours,
            LocalDate businessDate
    ) {
    }

    public record EffectiveContractResponse(
            Long contractId,
            Long employeeId,
            Long regionId,
            String employmentType,
            String salaryType,
            BigDecimal baseSalary,
            String taxCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record ApprovedAttendanceResponse(
            Long approvalId,
            Long shiftAssignmentId,
            Long employeeId,
            Long regionId,
            Long outletId,
            Long contractId,
            LocalDate businessDate,
            String attendanceStatus,
            BigDecimal workHours,
            BigDecimal overtimeHours
    ) {
    }

    public record AttendanceApprovalListResponse(
            List<ApprovedAttendanceResponse> items
    ) {
    }
}
