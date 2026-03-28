package com.fern.hrservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.hrservice.dto.HrCommands.CreateAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateContractRequest;
import com.fern.hrservice.dto.HrCommands.CreateEmployeeRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftScheduleRequest;
import com.fern.hrservice.dto.HrCommands.RecordAttendanceEventRequest;
import com.fern.hrservice.dto.HrResponses.ApprovedAttendanceResponse;
import com.fern.hrservice.dto.HrResponses.AssignmentResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceApprovalResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceEventResponse;
import com.fern.hrservice.dto.HrResponses.ContractResponse;
import com.fern.hrservice.dto.HrResponses.EffectiveContractResponse;
import com.fern.hrservice.dto.HrResponses.EmployeeResponse;
import com.fern.hrservice.dto.HrResponses.ShiftAssignmentResponse;
import com.fern.hrservice.dto.HrResponses.ShiftScheduleResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.AttendanceApprovedEvent;
import com.fern.platform.observability.CorrelationId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HrService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final HrAuthorizer hrAuthorizer;
    private final HrOrgClient hrOrgClient;
    private final HrAuditService hrAuditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public HrService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            HrAuthorizer hrAuthorizer,
            HrOrgClient hrOrgClient,
            HrAuditService hrAuditService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.hrAuthorizer = hrAuthorizer;
        this.hrOrgClient = hrOrgClient;
        this.hrAuditService = hrAuditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional("masterTransactionManager")
    public EmployeeResponse createEmployee(FernPrincipal principal, CreateEmployeeRequest request) {
        hrAuthorizer.requireSystemPermission(principal, PermissionCodes.HR_EMPLOYEE_WRITE);
        Long id = insertForId(masterJdbcTemplate, """
                INSERT INTO hr_master.employee_profile (
                    employee_code, user_account_id, full_name, dob, gender, email, phone, status, hired_at, created_at, updated_at
                ) VALUES (
                    :employeeCode, :userAccountId, :fullName, :dob, :gender, :email, :phone, :status, :hiredAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "employeeCode", request.employeeCode() == null || request.employeeCode().isBlank() ? "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : request.employeeCode(),
                "userAccountId", request.userAccountId(),
                "fullName", request.fullName(),
                "dob", request.dob(),
                "gender", normalize(request.gender(), "UNKNOWN"),
                "email", request.email(),
                "phone", request.phone(),
                "status", normalize(request.status(), "ACTIVE"),
                "hiredAt", request.hiredAt()
        ));
        EmployeeResponse response = getEmployee(principal, id);
        hrAuditService.publish("hr.employee.created", principal, null, null, "CREATE", "EMPLOYEE", id.toString(), null, response, Map.of("employeeCode", response.employeeCode()));
        return response;
    }

    @Transactional("masterTransactionManager")
    public ContractResponse createContract(FernPrincipal principal, CreateContractRequest request) {
        hrAuthorizer.requireSystemPermission(principal, PermissionCodes.HR_CONTRACT_WRITE);
        requireEmployee(request.employeeId());
        ensureNoOverlappingContracts(request.employeeId(), request.startDate(), request.endDate());
        Long id = insertForId(masterJdbcTemplate, """
                INSERT INTO hr_master.employee_contract (
                    employee_id, employment_type, salary_type, base_salary, region_id, tax_code, contract_status,
                    start_date, end_date, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :employeeId, :employmentType, :salaryType, :baseSalary, :regionId, :taxCode, :contractStatus,
                    :startDate, :endDate, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "employeeId", request.employeeId(),
                "employmentType", request.employmentType(),
                "salaryType", request.salaryType(),
                "baseSalary", request.baseSalary().setScale(2, RoundingMode.HALF_UP),
                "regionId", request.regionId(),
                "taxCode", request.taxCode(),
                "contractStatus", normalize(request.contractStatus(), "ACTIVE"),
                "startDate", request.startDate(),
                "endDate", request.endDate(),
                "createdByUserId", principal == null ? null : principal.userId()
        ));
        ContractResponse response = listContracts(principal, request.employeeId()).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Contract not found after insert"));
        hrAuditService.publish("hr.contract.created", principal, request.regionId(), null, "CREATE", "EMPLOYEE_CONTRACT", id.toString(), null, response, Map.of("employeeId", request.employeeId()));
        return response;
    }

    @Transactional
    public AssignmentResponse createAssignment(FernPrincipal principal, CreateAssignmentRequest request) {
        HrOrgClient.OutletRoute outlet = hrOrgClient.requireOutlet(request.outletId());
        hrAuthorizer.requireRegionPermission(principal, outlet.regionId(), PermissionCodes.HR_SHIFT_WRITE);
        if (!outlet.regionId().equals(request.regionId())) {
            throw new ConflictException("Assignment route does not match outlet route");
        }
        requireEmployee(request.employeeId());
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO hr.employee_assignment (
                    employee_id, region_id, outlet_id, position_title, start_date, end_date, is_primary, status, created_at, updated_at
                ) VALUES (
                    :employeeId, :regionId, :outletId, :positionTitle, :startDate, :endDate, :primaryAssignment, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "employeeId", request.employeeId(),
                "regionId", outlet.regionId(),
                "outletId", request.outletId(),
                "positionTitle", request.positionTitle(),
                "startDate", request.startDate(),
                "endDate", request.endDate(),
                "primaryAssignment", request.primaryAssignment() == null ? Boolean.FALSE : request.primaryAssignment(),
                "status", normalize(request.status(), "ACTIVE")
        ));
        AssignmentResponse response = requireAssignment(id);
        hrAuditService.publish("hr.assignment.created", principal, outlet.regionId(), request.outletId(), "CREATE", "EMPLOYEE_ASSIGNMENT", id.toString(), null, response, Map.of("employeeId", request.employeeId()));
        return response;
    }

    @Transactional
    public ShiftScheduleResponse createShiftSchedule(FernPrincipal principal, CreateShiftScheduleRequest request) {
        HrOrgClient.OutletRoute outlet = hrOrgClient.requireOutlet(request.outletId());
        hrAuthorizer.requireOutletPermission(principal, request.outletId(), PermissionCodes.HR_SHIFT_WRITE);
        if (!outlet.regionId().equals(request.regionId())) {
            throw new ConflictException("Shift schedule route does not match outlet route");
        }
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO hr.shift_schedule (
                    region_id, outlet_id, shift_date, shift_name, start_time, end_time, status, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :regionId, :outletId, :shiftDate, :shiftName, :startTime, :endTime, :status, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "regionId", outlet.regionId(),
                "outletId", request.outletId(),
                "shiftDate", request.shiftDate(),
                "shiftName", request.shiftName(),
                "startTime", request.startTime(),
                "endTime", request.endTime(),
                "status", normalize(request.status(), "SCHEDULED"),
                "createdByUserId", principal == null ? null : principal.userId()
        ));
        return getShiftSchedule(principal, id);
    }

    @Transactional
    public ShiftAssignmentResponse createShiftAssignment(FernPrincipal principal, CreateShiftAssignmentRequest request) {
        ShiftScheduleResponse schedule = requireShiftSchedule(request.shiftScheduleId());
        hrAuthorizer.requireOutletPermission(principal, schedule.outletId(), PermissionCodes.HR_SHIFT_WRITE);
        ensureNoShiftConflict(request.employeeId(), schedule.shiftDate(), schedule.startTime(), schedule.endTime());
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO hr.shift_assignment (
                    shift_schedule_id, employee_id, assigned_role, attendance_status, approval_status, note, created_at, updated_at
                ) VALUES (
                    :shiftScheduleId, :employeeId, :assignedRole, 'PENDING', 'PENDING', :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "shiftScheduleId", request.shiftScheduleId(),
                "employeeId", request.employeeId(),
                "assignedRole", request.assignedRole(),
                "note", request.note()
        ));
        return getShiftAssignment(principal, id);
    }

    @Transactional
    public AttendanceEventResponse recordAttendanceEvent(FernPrincipal principal, RecordAttendanceEventRequest request) {
        ShiftAssignmentRecord assignment = requireShiftAssignment(request.shiftAssignmentId());
        hrAuthorizer.requireOutletPermission(principal, assignment.outletId(), PermissionCodes.HR_ATTENDANCE_WRITE);
        if (!assignment.employeeId().equals(request.employeeId())) {
            throw new ConflictException("Attendance employee does not match shift assignment");
        }
        if (!assignment.regionId().equals(request.regionId()) || !assignment.outletId().equals(request.outletId())) {
            throw new ConflictException("Attendance route does not match shift assignment");
        }
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO hr.attendance_event (
                    employee_id, region_id, outlet_id, shift_assignment_id, event_type, event_time, source_system, created_by_user_id, created_at
                ) VALUES (
                    :employeeId, :regionId, :outletId, :shiftAssignmentId, :eventType, :eventTime, :sourceSystem, :createdByUserId, CURRENT_TIMESTAMP
                )
                """, params(
                "employeeId", request.employeeId(),
                "regionId", assignment.regionId(),
                "outletId", assignment.outletId(),
                "shiftAssignmentId", request.shiftAssignmentId(),
                "eventType", request.eventType(),
                "eventTime", request.eventTime(),
                "sourceSystem", request.sourceSystem(),
                "createdByUserId", principal == null ? null : principal.userId()
        ));
        jdbcTemplate.update("""
                UPDATE hr.shift_assignment
                SET attendance_status = 'PRESENT', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND attendance_status = 'PENDING'
                """, params("id", request.shiftAssignmentId()));
        AttendanceEventResponse response = new AttendanceEventResponse(
                id,
                request.employeeId(),
                request.shiftAssignmentId(),
                request.eventType(),
                request.eventTime(),
                request.sourceSystem()
        );
        hrAuditService.publish("hr.attendance.recorded", principal, assignment.regionId(), assignment.outletId(), "CREATE", "ATTENDANCE_EVENT", id.toString(), null, response, Map.of("shiftAssignmentId", request.shiftAssignmentId()));
        return response;
    }

    @Transactional
    public AttendanceApprovalResponse reviewAttendance(FernPrincipal principal, Long shiftAssignmentId, String status, String comments) {
        return reviewAttendance(principal, shiftAssignmentId, status, comments, null);
    }

    @Transactional
    public AttendanceApprovalResponse reviewAttendance(
            FernPrincipal principal,
            Long shiftAssignmentId,
            String status,
            String comments,
            String correlationId
    ) {
        ShiftAssignmentRecord assignment = requireShiftAssignment(shiftAssignmentId);
        hrAuthorizer.requireOutletPermission(principal, assignment.outletId(), PermissionCodes.HR_ATTENDANCE_REVIEW);
        AttendanceComputation computation = computeAttendance(shiftAssignmentId, assignment);
        ensureApprovalRow(shiftAssignmentId);
        ApprovalRow currentApproval = queryApprovalForUpdate(shiftAssignmentId)
                .orElseThrow(() -> new IllegalStateException("Approval row not found after upsert"));
        if ("APPROVED".equals(currentApproval.status()) && !"APPROVED".equals(status)) {
            throw new BadRequestException("Approved attendance cannot be changed");
        }
        if (status.equals(currentApproval.status())) {
            return mapAttendanceApproval(
                    currentApproval.id(),
                    assignment,
                    computation,
                    currentApproval.status(),
                    currentApproval.comments(),
                    currentApproval.approvedByUserId(),
                    currentApproval.approvedAt()
            );
        }
        Instant approvedAt = "APPROVED".equals(status) ? clock.instant() : null;
        Long approvedByUserId = "APPROVED".equals(status) ? principal == null ? null : principal.userId() : null;
        jdbcTemplate.update("""
                UPDATE hr.attendance_approval
                SET status = :status,
                    comments = :comments,
                    approved_by_user_id = :approvedByUserId,
                    approved_at = :approvedAt,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "status", status,
                "comments", comments,
                "approvedByUserId", approvedByUserId,
                "approvedAt", approvedAt,
                "id", currentApproval.id()
        ));
        jdbcTemplate.update("""
                UPDATE hr.shift_assignment
                SET approval_status = :status,
                    attendance_status = :attendanceStatus,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "status", status,
                "attendanceStatus", "APPROVED".equals(status) ? computation.attendanceStatus() : "PENDING",
                "id", shiftAssignmentId
        ));
        AttendanceApprovalResponse response = mapAttendanceApproval(
                currentApproval.id(),
                assignment,
                computation,
                status,
                comments,
                approvedByUserId,
                approvedAt
        );
        if ("APPROVED".equals(status)) {
            EffectiveContractResponse contract = activeContractForDate(assignment.employeeId(), assignment.shiftDate()).orElse(null);
            AttendanceApprovedEvent event = new AttendanceApprovedEvent(
                    UUID.randomUUID().toString(),
                    "attendance.approved",
                    clock.instant(),
                    "hr-service",
                    correlationId,
                    UUID.randomUUID().toString(),
                    currentApproval.id(),
                    shiftAssignmentId,
                    assignment.employeeId(),
                    assignment.regionId(),
                    assignment.outletId(),
                    assignment.shiftDate(),
                    computation.attendanceStatus(),
                    computation.workHours(),
                    computation.overtimeHours(),
                    contract == null ? null : contract.contractId(),
                    principal == null ? null : principal.userId()
            );
            enqueueOutbox("ATTENDANCE_APPROVAL", currentApproval.id().toString(), "attendance.approved", assignment.outletId().toString(), event);
        }
        hrAuditService.publish(
                "APPROVED".equals(status) ? "hr.attendance.approved" : "hr.attendance.rejected",
                principal,
                correlationId,
                assignment.regionId(),
                assignment.outletId(),
                status,
                "ATTENDANCE_APPROVAL",
                currentApproval.id().toString(),
                null,
                response,
                Map.of("shiftAssignmentId", shiftAssignmentId)
        );
        return response;
    }

    public EmployeeResponse getEmployee(FernPrincipal principal, Long id) {
        hrAuthorizer.requireSystemPermission(principal, PermissionCodes.HR_EMPLOYEE_READ);
        return requireEmployee(id);
    }

    public List<ContractResponse> listContracts(FernPrincipal principal, Long employeeId) {
        hrAuthorizer.requirePermission(principal, PermissionCodes.HR_CONTRACT_READ);
        requireEmployee(employeeId);
        List<ContractResponse> contracts = masterJdbcTemplate.query("""
                SELECT id, employee_id, employment_type, salary_type, base_salary, region_id, tax_code, contract_status, start_date, end_date
                FROM hr_master.employee_contract
                WHERE employee_id = :employeeId
                ORDER BY start_date DESC, id DESC
                """, params("employeeId", employeeId), (rs, rowNum) -> mapContract(rs));
        if (principal != null && principal.scopeRoots().system()) {
            return contracts;
        }
        return contracts.stream()
                .filter(contract -> contract.regionId() != null && principal != null && principal.scopeRoots().regions().contains(contract.regionId()))
                .toList();
    }

    public List<AssignmentResponse> listAssignments(FernPrincipal principal, Long employeeId) {
        hrAuthorizer.requirePermission(principal, PermissionCodes.HR_SHIFT_READ);
        requireEmployee(employeeId);
        List<AssignmentResponse> assignments = jdbcTemplate.query("""
                SELECT id, employee_id, region_id, outlet_id, position_title, start_date, end_date, is_primary, status
                FROM hr.employee_assignment
                WHERE employee_id = :employeeId
                ORDER BY start_date DESC, id DESC
                """, params("employeeId", employeeId), (rs, rowNum) -> new AssignmentResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("position_title"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getBoolean("is_primary"),
                rs.getString("status")
        ));
        if (principal != null && principal.scopeRoots().system()) {
            return assignments;
        }
        return assignments.stream()
                .filter(assignment -> isRegionVisible(principal, assignment.regionId()) || isOutletVisible(principal, assignment.outletId()))
                .toList();
    }

    public ShiftScheduleResponse getShiftSchedule(FernPrincipal principal, Long id) {
        ShiftScheduleResponse response = requireShiftSchedule(id);
        hrAuthorizer.requireOutletPermission(principal, response.outletId(), PermissionCodes.HR_SHIFT_READ);
        return response;
    }

    public ShiftAssignmentResponse getShiftAssignment(FernPrincipal principal, Long id) {
        ShiftAssignmentRecord record = requireShiftAssignment(id);
        hrAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.HR_SHIFT_READ);
        return toShiftAssignmentResponse(record);
    }

    public AttendanceApprovalResponse getAttendanceApproval(FernPrincipal principal, Long shiftAssignmentId) {
        ShiftAssignmentRecord assignment = requireShiftAssignment(shiftAssignmentId);
        hrAuthorizer.requireOutletPermission(principal, assignment.outletId(), PermissionCodes.HR_ATTENDANCE_REVIEW);
        AttendanceComputation computation = computeAttendance(shiftAssignmentId, assignment);
        ApprovalRow approval = queryApproval(shiftAssignmentId)
                .orElse(new ApprovalRow(null, "PENDING", null, null, null));
        return mapAttendanceApproval(
                approval.id(),
                assignment,
                computation,
                approval.status(),
                approval.comments(),
                approval.approvedByUserId(),
                approval.approvedAt()
        );
    }

    public List<AttendanceApprovalResponse> listAttendanceApprovals(FernPrincipal principal, Long regionId, Long outletId) {
        if (outletId != null) {
            hrAuthorizer.requireOutletPermission(principal, outletId, PermissionCodes.HR_ATTENDANCE_REVIEW);
        } else if (regionId != null) {
            hrAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.HR_ATTENDANCE_REVIEW);
        } else {
            hrAuthorizer.requireSystemPermission(principal, PermissionCodes.HR_ATTENDANCE_REVIEW);
        }
        String sql = """
                SELECT sa.id AS shift_assignment_id,
                       sa.employee_id,
                       s.region_id,
                       s.outlet_id,
                       s.shift_date,
                       s.start_time,
                       s.end_time,
                       sa.attendance_status,
                       aa.id AS approval_id,
                       COALESCE(aa.status, 'PENDING') AS approval_status,
                       aa.comments,
                       aa.approved_by_user_id,
                       aa.approved_at
                FROM hr.shift_assignment sa
                JOIN hr.shift_schedule s ON s.id = sa.shift_schedule_id
                LEFT JOIN hr.attendance_approval aa ON aa.shift_assignment_id = sa.id
                WHERE (:regionId IS NULL OR s.region_id = :regionId)
                  AND (:outletId IS NULL OR s.outlet_id = :outletId)
                ORDER BY s.shift_date DESC, sa.id DESC
                """;
        return jdbcTemplate.query(sql, params("regionId", regionId, "outletId", outletId), (rs, rowNum) -> {
            ShiftAssignmentRecord assignment = new ShiftAssignmentRecord(
                    rs.getLong("shift_assignment_id"),
                    null,
                    rs.getLong("employee_id"),
                    rs.getLong("region_id"),
                    rs.getLong("outlet_id"),
                    rs.getObject("shift_date", LocalDate.class),
                    rs.getObject("start_time", LocalTime.class),
                    rs.getObject("end_time", LocalTime.class),
                    rs.getString("attendance_status"),
                    rs.getString("approval_status"),
                    null
            );
            AttendanceComputation computation = computeAttendance(assignment.id(), assignment);
            return new AttendanceApprovalResponse(
                    nullableLong(rs, "approval_id"),
                    assignment.id(),
                    rs.getString("approval_status"),
                    rs.getString("comments"),
                    instant(rs, "approved_at"),
                    nullableLong(rs, "approved_by_user_id"),
                    computation.attendanceStatus(),
                    computation.workHours(),
                    computation.overtimeHours(),
                    assignment.shiftDate()
            );
        });
    }

    public List<EffectiveContractResponse> findEffectiveContracts(Long regionId, LocalDate startDate, LocalDate endDate) {
        return masterJdbcTemplate.query("""
                SELECT id, employee_id, region_id, employment_type, salary_type, base_salary, tax_code, start_date, end_date
                FROM hr_master.employee_contract
                WHERE contract_status = 'ACTIVE'
                  AND region_id = :regionId
                  AND start_date <= :endDate
                  AND (end_date IS NULL OR end_date >= :startDate)
                ORDER BY employee_id, start_date
                """, params(
                "regionId", regionId,
                "startDate", startDate,
                "endDate", endDate
        ), (rs, rowNum) -> new EffectiveContractResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                nullableLong(rs, "region_id"),
                rs.getString("employment_type"),
                rs.getString("salary_type"),
                rs.getBigDecimal("base_salary"),
                rs.getString("tax_code"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class)
        ));
    }

    public List<ApprovedAttendanceResponse> findApprovedAttendance(Long regionId, LocalDate startDate, LocalDate endDate) {
        return queryApprovedAttendance("""
                WHERE s.region_id = :regionId
                  AND s.shift_date BETWEEN :startDate AND :endDate
                  AND aa.status = 'APPROVED'
                """, params("regionId", regionId, "startDate", startDate, "endDate", endDate));
    }

    public List<ApprovedAttendanceResponse> findApprovedAttendanceByEmployee(Long employeeId, LocalDate at) {
        return queryApprovedAttendance("""
                WHERE sa.employee_id = :employeeId
                  AND s.shift_date = :at
                  AND aa.status = 'APPROVED'
                """, params("employeeId", employeeId, "at", at));
    }

    private List<ApprovedAttendanceResponse> queryApprovedAttendance(String whereClause, MapSqlParameterSource params) {
        String sql = """
                SELECT sa.id AS shift_assignment_id,
                       sa.employee_id,
                       s.region_id,
                       s.outlet_id,
                       s.shift_date,
                       s.start_time,
                       s.end_time,
                       sa.attendance_status,
                       aa.id AS approval_id
                FROM hr.shift_assignment sa
                JOIN hr.shift_schedule s ON s.id = sa.shift_schedule_id
                JOIN hr.attendance_approval aa ON aa.shift_assignment_id = sa.id
                """ + whereClause + """
                ORDER BY s.shift_date, sa.id
                """;
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            ShiftAssignmentRecord assignment = new ShiftAssignmentRecord(
                    rs.getLong("shift_assignment_id"),
                    null,
                    rs.getLong("employee_id"),
                    rs.getLong("region_id"),
                    rs.getLong("outlet_id"),
                    rs.getObject("shift_date", LocalDate.class),
                    rs.getObject("start_time", LocalTime.class),
                    rs.getObject("end_time", LocalTime.class),
                    rs.getString("attendance_status"),
                    "APPROVED",
                    null
            );
            AttendanceComputation computation = computeAttendance(assignment.id(), assignment);
            EffectiveContractResponse contract = activeContractForDate(assignment.employeeId(), assignment.shiftDate()).orElse(null);
            return new ApprovedAttendanceResponse(
                    rs.getLong("approval_id"),
                    assignment.id(),
                    assignment.employeeId(),
                    assignment.regionId(),
                    assignment.outletId(),
                    contract == null ? null : contract.contractId(),
                    assignment.shiftDate(),
                    computation.attendanceStatus(),
                    computation.workHours(),
                    computation.overtimeHours()
            );
        });
    }

    private EmployeeResponse requireEmployee(Long id) {
        return masterJdbcTemplate.query("""
                SELECT id, employee_code, full_name, dob, gender, email, phone, status, hired_at, user_account_id
                FROM hr_master.employee_profile
                WHERE id = :id AND deleted_at IS NULL
                """, params("id", id), rs -> rs.next() ? new EmployeeResponse(
                rs.getLong("id"),
                rs.getString("employee_code"),
                rs.getString("full_name"),
                rs.getObject("dob", LocalDate.class),
                rs.getString("gender"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("status"),
                rs.getObject("hired_at", LocalDate.class),
                nullableLong(rs, "user_account_id")
        ) : null);
    }

    private AssignmentResponse requireAssignment(Long id) {
        AssignmentResponse response = jdbcTemplate.query("""
                SELECT id, employee_id, region_id, outlet_id, position_title, start_date, end_date, is_primary, status
                FROM hr.employee_assignment
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new AssignmentResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("position_title"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getBoolean("is_primary"),
                rs.getString("status")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Employee assignment not found");
        }
        return response;
    }

    private void ensureNoOverlappingContracts(Long employeeId, LocalDate startDate, LocalDate endDate) {
        Integer count = masterJdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM hr_master.employee_contract
                WHERE employee_id = :employeeId
                  AND COALESCE(end_date, DATE '2999-12-31') >= :startDate
                  AND COALESCE(:endDate, DATE '2999-12-31') >= start_date
                  AND contract_status IN ('ACTIVE', 'DRAFT')
                """, params("employeeId", employeeId, "startDate", startDate, "endDate", endDate), Integer.class);
        if (count != null && count > 0) {
            throw new ConflictException("Employee already has an overlapping contract");
        }
    }

    private ShiftScheduleResponse requireShiftSchedule(Long id) {
        ShiftScheduleResponse response = jdbcTemplate.query("""
                SELECT id, region_id, outlet_id, shift_date, shift_name, start_time, end_time, status
                FROM hr.shift_schedule
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new ShiftScheduleResponse(
                rs.getLong("id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getObject("shift_date", LocalDate.class),
                rs.getString("shift_name"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getString("status")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Shift schedule not found");
        }
        return response;
    }

    private ShiftAssignmentRecord requireShiftAssignment(Long id) {
        ShiftAssignmentRecord response = jdbcTemplate.query("""
                SELECT sa.id,
                       sa.shift_schedule_id,
                       sa.employee_id,
                       s.region_id,
                       s.outlet_id,
                       s.shift_date,
                       s.start_time,
                       s.end_time,
                       sa.attendance_status,
                       sa.approval_status,
                       sa.note
                FROM hr.shift_assignment sa
                JOIN hr.shift_schedule s ON s.id = sa.shift_schedule_id
                WHERE sa.id = :id
                """, params("id", id), rs -> rs.next() ? new ShiftAssignmentRecord(
                rs.getLong("id"),
                rs.getLong("shift_schedule_id"),
                rs.getLong("employee_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getObject("shift_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getString("attendance_status"),
                rs.getString("approval_status"),
                rs.getString("note")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Shift assignment not found");
        }
        return response;
    }

    private ShiftAssignmentResponse requireShiftAssignmentResponse(Long id) {
        return toShiftAssignmentResponse(requireShiftAssignment(id));
    }

    private ShiftAssignmentResponse toShiftAssignmentResponse(ShiftAssignmentRecord record) {
        return new ShiftAssignmentResponse(
                record.id(),
                record.shiftScheduleId(),
                record.employeeId(),
                null,
                record.attendanceStatus(),
                record.approvalStatus(),
                record.note()
        );
    }

    private void ensureNoShiftConflict(Long employeeId, LocalDate shiftDate, LocalTime startTime, LocalTime endTime) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM hr.shift_assignment sa
                JOIN hr.shift_schedule s ON s.id = sa.shift_schedule_id
                WHERE sa.employee_id = :employeeId
                  AND s.shift_date = :shiftDate
                  AND s.status <> 'CANCELLED'
                  AND (
                        (s.start_time <= :startTime AND s.end_time > :startTime)
                     OR (s.start_time < :endTime AND s.end_time >= :endTime)
                     OR (s.start_time >= :startTime AND s.end_time <= :endTime)
                  )
                """, params(
                "employeeId", employeeId,
                "shiftDate", shiftDate,
                "startTime", startTime,
                "endTime", endTime
        ), Integer.class);
        if (count != null && count > 0) {
            throw new ConflictException("Employee already has a conflicting shift");
        }
    }

    private AttendanceComputation computeAttendance(Long shiftAssignmentId, ShiftAssignmentRecord assignment) {
        List<AttendanceEventRow> events = jdbcTemplate.query("""
                SELECT event_type, event_time
                FROM hr.attendance_event
                WHERE shift_assignment_id = :shiftAssignmentId
                ORDER BY event_time
                """, params("shiftAssignmentId", shiftAssignmentId), (rs, rowNum) -> new AttendanceEventRow(
                rs.getString("event_type"),
                instant(rs, "event_time")
        ));
        Optional<Instant> clockIn = events.stream().filter(item -> "CLOCK_IN".equals(item.eventType())).map(AttendanceEventRow::eventTime).min(Comparator.naturalOrder());
        Optional<Instant> clockOut = events.stream().filter(item -> "CLOCK_OUT".equals(item.eventType())).map(AttendanceEventRow::eventTime).max(Comparator.naturalOrder());
        if (clockIn.isEmpty() || clockOut.isEmpty()) {
            return new AttendanceComputation("ABSENT", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal hours = BigDecimal.valueOf(Duration.between(clockIn.get(), clockOut.get()).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal scheduled = BigDecimal.valueOf(Duration.between(
                LocalDateTime.of(assignment.shiftDate(), assignment.startTime()).toInstant(ZoneOffset.UTC),
                LocalDateTime.of(assignment.shiftDate(), assignment.endTime()).toInstant(ZoneOffset.UTC)
        ).toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        if (scheduled.signum() < 0) {
            scheduled = hours;
        }
        BigDecimal overtime = hours.subtract(scheduled).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime shiftStart = LocalDateTime.of(assignment.shiftDate(), assignment.startTime());
        LocalDateTime actualIn = LocalDateTime.ofInstant(clockIn.get(), ZoneOffset.UTC);
        String attendanceStatus = actualIn.isAfter(shiftStart.plusMinutes(5)) ? "LATE" : "PRESENT";
        return new AttendanceComputation(attendanceStatus, hours.setScale(2, RoundingMode.HALF_UP), overtime);
    }

    private Optional<EffectiveContractResponse> activeContractForDate(Long employeeId, LocalDate businessDate) {
        List<EffectiveContractResponse> contracts = masterJdbcTemplate.query("""
                SELECT id, employee_id, region_id, employment_type, salary_type, base_salary, tax_code, start_date, end_date
                FROM hr_master.employee_contract
                WHERE employee_id = :employeeId
                  AND contract_status = 'ACTIVE'
                  AND start_date <= :businessDate
                  AND (end_date IS NULL OR end_date >= :businessDate)
                ORDER BY start_date DESC, id DESC
                LIMIT 1
                """, params("employeeId", employeeId, "businessDate", businessDate), (rs, rowNum) -> new EffectiveContractResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                nullableLong(rs, "region_id"),
                rs.getString("employment_type"),
                rs.getString("salary_type"),
                rs.getBigDecimal("base_salary"),
                rs.getString("tax_code"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class)
        ));
        return contracts.stream().findFirst();
    }

    private Optional<Long> currentApprovalId(Long shiftAssignmentId) {
        return Optional.ofNullable(jdbcTemplate.query("""
                SELECT id
                FROM hr.attendance_approval
                WHERE shift_assignment_id = :shiftAssignmentId
                """, params("shiftAssignmentId", shiftAssignmentId), rs -> rs.next() ? rs.getLong("id") : null));
    }

    private void ensureApprovalRow(Long shiftAssignmentId) {
        jdbcTemplate.update("""
                INSERT INTO hr.attendance_approval (
                    shift_assignment_id, status, created_at, updated_at
                ) VALUES (
                    :shiftAssignmentId, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                ON CONFLICT (shift_assignment_id) DO NOTHING
                """, params("shiftAssignmentId", shiftAssignmentId));
    }

    private Optional<ApprovalRow> queryApproval(Long shiftAssignmentId) {
        return Optional.ofNullable(jdbcTemplate.query("""
                SELECT id, status, comments, approved_by_user_id, approved_at
                FROM hr.attendance_approval
                WHERE shift_assignment_id = :shiftAssignmentId
                """, params("shiftAssignmentId", shiftAssignmentId), rs -> rs.next() ? new ApprovalRow(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getString("comments"),
                nullableLong(rs, "approved_by_user_id"),
                instant(rs, "approved_at")
        ) : null));
    }

    private Optional<ApprovalRow> queryApprovalForUpdate(Long shiftAssignmentId) {
        return Optional.ofNullable(jdbcTemplate.query("""
                SELECT id, status, comments, approved_by_user_id, approved_at
                FROM hr.attendance_approval
                WHERE shift_assignment_id = :shiftAssignmentId
                FOR UPDATE
                """, params("shiftAssignmentId", shiftAssignmentId), rs -> rs.next() ? new ApprovalRow(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getString("comments"),
                nullableLong(rs, "approved_by_user_id"),
                instant(rs, "approved_at")
        ) : null));
    }

    private AttendanceApprovalResponse mapAttendanceApproval(
            Long approvalId,
            ShiftAssignmentRecord assignment,
            AttendanceComputation computation,
            String status,
            String comments,
            Long approvedByUserId,
            Instant approvedAt
    ) {
        return new AttendanceApprovalResponse(
                approvalId,
                assignment.id(),
                status,
                comments,
                approvedAt,
                approvedByUserId,
                computation.attendanceStatus(),
                computation.workHours(),
                computation.overtimeHours(),
                assignment.shiftDate()
        );
    }

    private ContractResponse mapContract(ResultSet rs) throws java.sql.SQLException {
        return new ContractResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                rs.getString("employment_type"),
                rs.getString("salary_type"),
                rs.getBigDecimal("base_salary"),
                nullableLong(rs, "region_id"),
                rs.getString("tax_code"),
                rs.getString("contract_status"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class)
        );
    }

    private void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        jdbcTemplate.update("""
                INSERT INTO hr.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', CURRENT_TIMESTAMP
                )
                ON CONFLICT DO NOTHING
                """, params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "partitionKey", partitionKey,
                "payload", toJson(payload)
        ));
    }

    private Long insertForId(NamedParameterJdbcTemplate template, String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value == null) {
                parameters.addValue((String) values[index], null, Types.NULL);
            } else if (value instanceof Instant instant) {
                parameters.addValue((String) values[index], OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
    }

    private String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean isRegionVisible(FernPrincipal principal, Long regionId) {
        return principal != null && regionId != null && principal.scopeRoots().regions().contains(regionId);
    }

    private boolean isOutletVisible(FernPrincipal principal, Long outletId) {
        return principal != null && outletId != null && principal.scopeRoots().outlets().contains(outletId);
    }

    private Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private record ShiftAssignmentRecord(
            Long id,
            Long shiftScheduleId,
            Long employeeId,
            Long regionId,
            Long outletId,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            String attendanceStatus,
            String approvalStatus,
            String note
    ) {
    }

    private record AttendanceEventRow(String eventType, Instant eventTime) {
    }

    private record AttendanceComputation(String attendanceStatus, BigDecimal workHours, BigDecimal overtimeHours) {
    }

    private record ApprovalRow(Long id, String status, String comments, Long approvedByUserId, Instant approvedAt) {
    }
}
