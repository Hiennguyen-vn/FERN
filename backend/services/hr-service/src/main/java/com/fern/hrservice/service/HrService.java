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
import com.fern.hrservice.dto.HrResponses.AttendanceEventListItemResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceEventResponse;
import com.fern.hrservice.dto.HrResponses.ContractResponse;
import com.fern.hrservice.dto.HrResponses.EffectiveContractResponse;
import com.fern.hrservice.dto.HrResponses.EmployeeResponse;
import com.fern.hrservice.dto.HrResponses.ShiftAssignmentResponse;
import com.fern.hrservice.dto.HrResponses.ShiftScheduleResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.ScopeAccess;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HrService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final HrAuthorizer hrAuthorizer;
    private final HrOrgClient hrOrgClient;
    private final HrAuditService hrAuditService;
    private final HrAttendanceService hrAttendanceService;

    public HrService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            HrAuthorizer hrAuthorizer,
            HrOrgClient hrOrgClient,
            HrAuditService hrAuditService,
            HrAttendanceService hrAttendanceService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.hrAuthorizer = hrAuthorizer;
        this.hrOrgClient = hrOrgClient;
        this.hrAuditService = hrAuditService;
        this.hrAttendanceService = hrAttendanceService;
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
        validateDateRange(request.startDate(), request.endDate(), "Contract");
        lockEmployeeContractWrites(request.employeeId());
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
        ContractResponse response = requireContract(id);
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
        validateDateRange(request.startDate(), request.endDate(), "Assignment");
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
        return requireShiftSchedule(id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ShiftAssignmentResponse createShiftAssignment(FernPrincipal principal, CreateShiftAssignmentRequest request) {
        try {
            ShiftScheduleResponse schedule = requireShiftSchedule(request.shiftScheduleId());
            hrAuthorizer.requireOutletPermission(principal, schedule.outletId(), PermissionCodes.HR_SHIFT_WRITE);
            lockShiftAssignmentWrites(request.employeeId(), schedule.shiftDate());
            ensureNoShiftConflict(request.employeeId(), schedule.shiftDate(), schedule.startTime(), schedule.endTime());
            Long id;
            try {
                id = insertForId(jdbcTemplate, """
                        INSERT INTO hr.shift_assignment (
                            shift_schedule_id, employee_id, assigned_role, attendance_status, approval_status, note, shift_start_at, shift_end_at, created_at, updated_at
                        ) VALUES (
                            :shiftScheduleId, :employeeId, :assignedRole, 'PENDING', 'PENDING', :note, :shiftStartAt, :shiftEndAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """, params(
                        "shiftScheduleId", request.shiftScheduleId(),
                        "employeeId", request.employeeId(),
                        "assignedRole", request.assignedRole(),
                        "note", request.note(),
                        "shiftStartAt", LocalDateTime.of(schedule.shiftDate(), schedule.startTime()),
                        "shiftEndAt", resolveShiftEndAt(schedule.shiftDate(), schedule.startTime(), schedule.endTime())
                ));
            } catch (DataIntegrityViolationException exception) {
                if (isShiftOverlapViolation(exception)) {
                    throw new ConflictException("Employee already has a conflicting shift");
                }
                throw exception;
            }
            return toShiftAssignmentResponse(requireShiftAssignment(id));
        } catch (ConcurrencyFailureException exception) {
            throw new ConflictException("Employee already has a conflicting shift");
        }
    }

    @Transactional
    public AttendanceEventResponse recordAttendanceEvent(
            FernPrincipal principal,
            String idempotencyKey,
            RecordAttendanceEventRequest request
    ) {
        return hrAttendanceService.recordAttendanceEvent(principal, idempotencyKey, request);
    }

    @Transactional
    public AttendanceApprovalResponse reviewAttendance(FernPrincipal principal, Long shiftAssignmentId, String status, String comments) {
        return hrAttendanceService.reviewAttendance(principal, shiftAssignmentId, status, comments);
    }

    @Transactional
    public AttendanceApprovalResponse reviewAttendance(
            FernPrincipal principal,
            Long shiftAssignmentId,
            String status,
            String comments,
            String correlationId
    ) {
        return hrAttendanceService.reviewAttendance(principal, shiftAssignmentId, status, comments, correlationId);
    }

    public EmployeeResponse getEmployee(FernPrincipal principal, Long id) {
        hrAuthorizer.requireSystemPermission(principal, PermissionCodes.HR_EMPLOYEE_READ);
        return requireEmployee(id);
    }

    public PageResponse<EmployeeResponse> listEmployees(FernPrincipal principal, String search, String status, Integer page, Integer size) {
        hrAuthorizer.requireSystemPermission(principal, PermissionCodes.HR_EMPLOYEE_READ);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null ? null : status.trim();
        int clampedSize = ListQueryDefaults.clampLimit(size);

        StringBuilder sql = new StringBuilder("""
                SELECT id, employee_code, full_name, dob, gender, email, phone, status, hired_at, user_account_id
                FROM hr_master.employee_profile
                WHERE deleted_at IS NULL
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (!normalizedSearch.isBlank()) {
            sql.append("""
                     AND (
                        CAST(id AS text) ILIKE :search
                        OR employee_code ILIKE :search
                        OR full_name ILIKE :search
                        OR COALESCE(email, '') ILIKE :search
                        OR COALESCE(phone, '') ILIKE :search
                     )
                    """);
            parameters.addValue("search", "%" + normalizedSearch + "%");
        }
        if (normalizedStatus != null && !normalizedStatus.isBlank()) {
            sql.append(" AND status = :status");
            parameters.addValue("status", normalizedStatus);
        }
        sql.append(" ORDER BY full_name ASC, id ASC");

        List<EmployeeResponse> items = masterJdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new EmployeeResponse(
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
        ));
        return toPageResponse(items, page, clampedSize);
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
        if (ScopeAccess.isSystemScoped(principal)) {
            return contracts;
        }
        return contracts.stream()
                .filter(contract -> ScopeAccess.allowsRegion(principal, contract.regionId()))
                .toList();
    }

    public PageResponse<ContractResponse> listContracts(
            FernPrincipal principal,
            Long employeeId,
            Long regionId,
            String search,
            String status,
            Integer page,
            Integer size
    ) {
        hrAuthorizer.requirePermission(principal, PermissionCodes.HR_CONTRACT_READ);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null ? null : status.trim();
        int clampedSize = ListQueryDefaults.clampLimit(size);

        StringBuilder sql = new StringBuilder("""
                SELECT c.id,
                       c.employee_id,
                       c.employment_type,
                       c.salary_type,
                       c.base_salary,
                       c.region_id,
                       c.tax_code,
                       c.contract_status,
                       c.start_date,
                       c.end_date,
                       e.employee_code,
                       e.full_name
                FROM hr_master.employee_contract c
                JOIN hr_master.employee_profile e ON e.id = c.employee_id
                WHERE e.deleted_at IS NULL
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (employeeId != null) {
            sql.append(" AND c.employee_id = :employeeId");
            parameters.addValue("employeeId", employeeId);
        }
        if (regionId != null) {
            sql.append(" AND c.region_id = :regionId");
            parameters.addValue("regionId", regionId);
        }
        if (normalizedStatus != null && !normalizedStatus.isBlank()) {
            sql.append(" AND c.contract_status = :status");
            parameters.addValue("status", normalizedStatus);
        }
        if (!normalizedSearch.isBlank()) {
            sql.append("""
                     AND (
                        CAST(c.id AS text) ILIKE :search
                        OR CAST(c.employee_id AS text) ILIKE :search
                        OR c.employment_type ILIKE :search
                        OR c.salary_type ILIKE :search
                        OR COALESCE(c.tax_code, '') ILIKE :search
                        OR e.employee_code ILIKE :search
                        OR e.full_name ILIKE :search
                     )
                    """);
            parameters.addValue("search", "%" + normalizedSearch + "%");
        }
        sql.append(" ORDER BY c.start_date DESC, c.id DESC");

        List<ContractResponse> items = masterJdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> mapContract(rs)).stream()
                .filter(contract -> ScopeAccess.isSystemScoped(principal) || ScopeAccess.allowsRegion(principal, contract.regionId()))
                .toList();
        return toPageResponse(items, page, clampedSize);
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
        if (ScopeAccess.isSystemScoped(principal)) {
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

    public List<ShiftScheduleResponse> listShiftSchedules(
            FernPrincipal principal, Long outletId, LocalDate fromDate, LocalDate toDate, int limit
    ) {
        hrAuthorizer.requirePermission(principal, PermissionCodes.HR_SHIFT_READ);
        StringBuilder sql = new StringBuilder("""
                SELECT id, region_id, outlet_id, shift_date, shift_name, start_time, end_time, status
                FROM hr.shift_schedule
                WHERE 1=1
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (outletId != null) {
            sql.append(" AND outlet_id = :outletId");
            parameters.addValue("outletId", outletId);
        }
        if (fromDate != null) {
            sql.append(" AND shift_date >= :fromDate");
            parameters.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            sql.append(" AND shift_date <= :toDate");
            parameters.addValue("toDate", toDate);
        }
        sql.append(" ORDER BY shift_date DESC, start_time ASC LIMIT :limit");
        parameters.addValue("limit", limit);
        List<ShiftScheduleResponse> schedules = jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new ShiftScheduleResponse(
                rs.getLong("id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getObject("shift_date", LocalDate.class),
                rs.getString("shift_name"),
                rs.getObject("start_time", LocalTime.class),
                rs.getObject("end_time", LocalTime.class),
                rs.getString("status")
        ));
        if (ScopeAccess.isSystemScoped(principal)) {
            return schedules;
        }
        return schedules.stream()
                .filter(s -> isOutletVisible(principal, s.outletId()) || isRegionVisible(principal, s.regionId()))
                .toList();
    }

    public List<ShiftAssignmentResponse> listShiftAssignments(
            FernPrincipal principal, Long shiftScheduleId, int limit
    ) {
        hrAuthorizer.requirePermission(principal, PermissionCodes.HR_SHIFT_READ);
        List<ShiftAssignmentRecord> records = jdbcTemplate.query("""
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
                WHERE sa.shift_schedule_id = :shiftScheduleId
                ORDER BY sa.id ASC
                LIMIT :limit
                """, params("shiftScheduleId", shiftScheduleId, "limit", limit), (rs, rowNum) -> new ShiftAssignmentRecord(
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
        ));
        return records.stream().map(this::toShiftAssignmentResponse).toList();
    }

    public AttendanceApprovalResponse getAttendanceApproval(FernPrincipal principal, Long shiftAssignmentId) {
        return hrAttendanceService.getAttendanceApproval(principal, shiftAssignmentId);
    }

    public List<AttendanceApprovalResponse> listAttendanceApprovals(FernPrincipal principal, Long regionId, Long outletId, int limit) {
        return hrAttendanceService.listAttendanceApprovals(principal, regionId, outletId, limit);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceEventListItemResponse> listAttendanceEvents(
            FernPrincipal principal,
            Long employeeId,
            Long shiftAssignmentId,
            Long regionId,
            Long outletId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sort
    ) {
        return hrAttendanceService.listAttendanceEvents(principal, employeeId, shiftAssignmentId, regionId, outletId, fromDate, toDate, page, size, sort);
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
        return hrAttendanceService.findApprovedAttendance(regionId, startDate, endDate);
    }

    public List<ApprovedAttendanceResponse> findApprovedAttendanceByEmployee(Long employeeId, LocalDate at) {
        return hrAttendanceService.findApprovedAttendanceByEmployee(employeeId, at);
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

    private ContractResponse requireContract(Long id) {
        ContractResponse response = masterJdbcTemplate.query("""
                SELECT id, employee_id, employment_type, salary_type, base_salary, region_id, tax_code, contract_status, start_date, end_date
                FROM hr_master.employee_contract
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? mapContract(rs) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Employee contract not found");
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

    private void lockEmployeeContractWrites(Long employeeId) {
        masterJdbcTemplate.update("""
                INSERT INTO hr_master.employee_contract_guard (employee_id, touched_at)
                VALUES (:employeeId, CURRENT_TIMESTAMP)
                ON CONFLICT (employee_id) DO NOTHING
                """, params("employeeId", employeeId));
        masterJdbcTemplate.update("""
                UPDATE hr_master.employee_contract_guard
                SET touched_at = CURRENT_TIMESTAMP
                WHERE employee_id = :employeeId
                """, params("employeeId", employeeId));
    }

    private void lockShiftAssignmentWrites(Long employeeId, LocalDate shiftDate) {
        jdbcTemplate.update("""
                INSERT INTO hr.shift_assignment_guard (employee_id, shift_date, touched_at)
                VALUES (:employeeId, :shiftDate, CURRENT_TIMESTAMP)
                ON CONFLICT (employee_id, shift_date) DO NOTHING
                """, params(
                "employeeId", employeeId,
                "shiftDate", shiftDate
        ));
        jdbcTemplate.update("""
                UPDATE hr.shift_assignment_guard
                SET touched_at = CURRENT_TIMESTAMP
                WHERE employee_id = :employeeId
                  AND shift_date = :shiftDate
                """, params(
                "employeeId", employeeId,
                "shiftDate", shiftDate
        ));
    }

    private LocalDateTime resolveShiftEndAt(LocalDate shiftDate, LocalTime startTime, LocalTime endTime) {
        LocalDate endDate = endTime.isAfter(startTime) ? shiftDate : shiftDate.plusDays(1);
        return LocalDateTime.of(endDate, endTime);
    }

    private boolean isShiftOverlapViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause() == null ? exception.getMessage() : exception.getMostSpecificCause().getMessage();
        return message != null && message.contains("hr_shift_assignment_no_overlap");
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate, String entityName) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException(entityName + " endDate must be on or after startDate");
        }
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
        return ScopeAccess.allowsRegion(principal, regionId);
    }

    private boolean isOutletVisible(FernPrincipal principal, Long outletId) {
        return ScopeAccess.allowsOutlet(principal, outletId);
    }

    private Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private <T> PageResponse<T> toPageResponse(List<T> items, Integer page, int size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int offset = Math.toIntExact(ListQueryDefaults.offsetFrom(page, size));
        if (offset >= items.size()) {
            return new PageResponse<>(List.of(), safePage, size, false);
        }
        int endExclusive = Math.min(items.size(), offset + size + 1);
        List<T> window = items.subList(offset, endExclusive);
        boolean hasMore = window.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(window.subList(0, size)) : List.copyOf(window);
        return new PageResponse<>(pagedItems, safePage, size, hasMore);
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

}
