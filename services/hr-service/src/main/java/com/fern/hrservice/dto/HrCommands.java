package com.fern.hrservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class HrCommands {
    private HrCommands() {
    }

    public record CreateEmployeeRequest(
            String employeeCode,
            @NotBlank String fullName,
            LocalDate dob,
            String gender,
            String email,
            String phone,
            String status,
            LocalDate hiredAt,
            Long userAccountId
    ) {
    }

    public record CreateContractRequest(
            @NotNull Long employeeId,
            @NotBlank String employmentType,
            @NotBlank String salaryType,
            @NotNull @DecimalMin("0.00") BigDecimal baseSalary,
            Long regionId,
            String taxCode,
            String contractStatus,
            @NotNull LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record CreateAssignmentRequest(
            @NotNull Long employeeId,
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotBlank String positionTitle,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean primaryAssignment,
            String status
    ) {
    }

    public record CreateShiftScheduleRequest(
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull LocalDate shiftDate,
            @NotBlank String shiftName,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            String status
    ) {
    }

    public record CreateShiftAssignmentRequest(
            @NotNull Long shiftScheduleId,
            @NotNull Long employeeId,
            String assignedRole,
            String note
    ) {
    }

    public record RecordAttendanceEventRequest(
            @NotNull Long employeeId,
            @NotNull Long regionId,
            @NotNull Long outletId,
            @NotNull Long shiftAssignmentId,
            @NotBlank String eventType,
            @NotNull Instant eventTime,
            String sourceSystem
    ) {
    }

    public record ReviewAttendanceRequest(
            String comments
    ) {
    }
}
