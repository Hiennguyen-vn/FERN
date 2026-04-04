package com.fern.hrservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.hrservice.dto.HrCommands.RecordAttendanceEventRequest;
import com.fern.hrservice.dto.HrResponses.ApprovedAttendanceResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceApprovalResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceEventListItemResponse;
import com.fern.hrservice.dto.HrResponses.AttendanceEventResponse;
import com.fern.hrservice.dto.HrResponses.EffectiveContractResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.ScopeAccess;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.AttendanceApprovedEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class HrAttendanceService {
    private static final int MAX_PAGE_SIZE = 200;
    private static final Duration ATTENDANCE_EARLY_WINDOW = Duration.ofHours(4);
    private static final Duration ATTENDANCE_LATE_WINDOW = Duration.ofHours(8);
    private static final Duration LATE_GRACE_PERIOD = Duration.ofMinutes(5);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final HrAuthorizer hrAuthorizer;
    private final HrAuditService hrAuditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId attendanceBusinessZone;

    HrAttendanceService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            HrAuthorizer hrAuthorizer,
            HrAuditService hrAuditService,
            ObjectMapper objectMapper,
            Clock clock,
            ZoneId attendanceBusinessZone
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.hrAuthorizer = hrAuthorizer;
        this.hrAuditService = hrAuditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.attendanceBusinessZone = attendanceBusinessZone;
    }

    @Transactional
    public AttendanceEventResponse recordAttendanceEvent(
            FernPrincipal principal,
            String idempotencyKey,
            RecordAttendanceEventRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        ShiftAssignmentRecord assignment = requireShiftAssignmentForUpdate(request.shiftAssignmentId());
        hrAuthorizer.requireOutletPermission(principal, assignment.outletId(), PermissionCodes.HR_ATTENDANCE_WRITE);
        if (!assignment.employeeId().equals(request.employeeId())) {
            throw new ConflictException("Attendance employee does not match shift assignment");
        }
        if (!assignment.regionId().equals(request.regionId()) || !assignment.outletId().equals(request.outletId())) {
            throw new ConflictException("Attendance route does not match shift assignment");
        }
        AttendanceEventRecord existingEvent = findAttendanceEventByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingEvent != null) {
            requireMatchingIdempotentAttendanceEvent(existingEvent, request);
            return toAttendanceEventResponse(existingEvent);
        }
        List<AttendanceEventRecord> existingEvents = attendanceEventsForShiftAssignment(request.shiftAssignmentId());
        AttendanceEventRecord concurrentExistingEvent = findAttendanceEventByIdempotencyKey(idempotencyKey).orElse(null);
        if (concurrentExistingEvent != null) {
            requireMatchingIdempotentAttendanceEvent(concurrentExistingEvent, request);
            return toAttendanceEventResponse(concurrentExistingEvent);
        }
        AttendanceAnalysis existingAnalysis = analyzeAttendance(assignment, existingEvents);
        if (!existingAnalysis.valid()) {
            throw new ConflictException(existingAnalysis.validationError());
        }
        validateAttendanceEventWindow(assignment, request.eventTime());
        validateNextAttendanceEvent(existingEvents, request);
        Long id;
        try {
            id = insertForId(jdbcTemplate, """
                    INSERT INTO hr.attendance_event (
                        employee_id, region_id, outlet_id, shift_assignment_id, event_type, event_time, source_system, created_by_user_id, created_at, idempotency_key
                    ) VALUES (
                        :employeeId, :regionId, :outletId, :shiftAssignmentId, :eventType, :eventTime, :sourceSystem, :createdByUserId, CURRENT_TIMESTAMP, :idempotencyKey
                    )
                    """, params(
                    "employeeId", request.employeeId(),
                    "regionId", assignment.regionId(),
                    "outletId", assignment.outletId(),
                    "shiftAssignmentId", request.shiftAssignmentId(),
                    "eventType", request.eventType(),
                    "eventTime", request.eventTime(),
                    "sourceSystem", request.sourceSystem(),
                    "createdByUserId", principal == null ? null : principal.userId(),
                    "idempotencyKey", idempotencyKey
            ));
        } catch (DataIntegrityViolationException exception) {
            AttendanceEventRecord persistedConcurrentEvent = findAttendanceEventByIdempotencyKey(idempotencyKey).orElse(null);
            if (persistedConcurrentEvent != null) {
                requireMatchingIdempotentAttendanceEvent(persistedConcurrentEvent, request);
                return toAttendanceEventResponse(persistedConcurrentEvent);
            }
            throw exception;
        }
        AttendanceEventRecord recordedEvent = requireAttendanceEventRecord(id);
        List<AttendanceEventRecord> updatedEvents = new ArrayList<>(existingEvents);
        updatedEvents.add(recordedEvent);
        AttendanceAnalysis previewAnalysis = analyzeAttendance(assignment, updatedEvents);
        jdbcTemplate.update("""
                UPDATE hr.shift_assignment
                SET attendance_status = :attendanceStatus, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("id", request.shiftAssignmentId(), "attendanceStatus", previewAnalysis.attendanceStatus()));
        AttendanceEventResponse response = toAttendanceEventResponse(recordedEvent);
        hrAuditService.publish(
                "hr.attendance.recorded",
                principal,
                assignment.regionId(),
                assignment.outletId(),
                "CREATE",
                "ATTENDANCE_EVENT",
                id.toString(),
                null,
                response,
                Map.of("shiftAssignmentId", request.shiftAssignmentId())
        );
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
        ShiftAssignmentRecord assignment = requireShiftAssignmentForUpdate(shiftAssignmentId);
        hrAuthorizer.requireOutletPermission(principal, assignment.outletId(), PermissionCodes.HR_ATTENDANCE_REVIEW);
        AttendanceAnalysis computation = analyzeAttendance(assignment, attendanceEventsForShiftAssignment(shiftAssignmentId));
        if ("APPROVED".equals(status) && !computation.approvable()) {
            throw new ConflictException(computation.validationError());
        }
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
        Long approvedByUserId = "APPROVED".equals(status) && principal != null ? principal.userId() : null;
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

    public AttendanceApprovalResponse getAttendanceApproval(FernPrincipal principal, Long shiftAssignmentId) {
        ShiftAssignmentRecord assignment = requireShiftAssignment(shiftAssignmentId);
        hrAuthorizer.requireOutletPermission(principal, assignment.outletId(), PermissionCodes.HR_ATTENDANCE_REVIEW);
        AttendanceAnalysis computation = analyzeAttendance(assignment, attendanceEventsForShiftAssignment(shiftAssignmentId));
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

    public List<AttendanceApprovalResponse> listAttendanceApprovals(FernPrincipal principal, Long regionId, Long outletId, int limit) {
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
                LIMIT :limit
                """;
        List<ShiftAssignmentRecord> assignments = new ArrayList<>();
        List<Object[]> rawRows = new ArrayList<>();
        jdbcTemplate.query(sql, params("regionId", regionId, "outletId", outletId, "limit", limit), (rs, rowNum) -> {
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
            assignments.add(assignment);
            rawRows.add(new Object[]{
                    nullableLong(rs, "approval_id"),
                    rs.getString("approval_status"),
                    rs.getString("comments"),
                    instant(rs, "approved_at"),
                    nullableLong(rs, "approved_by_user_id")
            });
            return null;
        });
        if (assignments.isEmpty()) {
            return List.of();
        }
        // Batch-load all attendance events for the returned shift assignments in one query
        List<Long> assignmentIds = assignments.stream().map(ShiftAssignmentRecord::id).toList();
        Map<Long, List<AttendanceEventRecord>> eventsByAssignmentId = batchLoadAttendanceEvents(assignmentIds);
        List<AttendanceApprovalResponse> results = new ArrayList<>(assignments.size());
        for (int i = 0; i < assignments.size(); i++) {
            ShiftAssignmentRecord assignment = assignments.get(i);
            Object[] row = rawRows.get(i);
            List<AttendanceEventRecord> events = eventsByAssignmentId.getOrDefault(assignment.id(), List.of());
            AttendanceAnalysis computation = analyzeAttendance(assignment, events);
            results.add(new AttendanceApprovalResponse(
                    (Long) row[0],
                    assignment.id(),
                    (String) row[1],
                    (String) row[2],
                    (Instant) row[3],
                    (Long) row[4],
                    computation.attendanceStatus(),
                    computation.workHours(),
                    computation.overtimeHours(),
                    assignment.shiftDate()
            ));
        }
        return results;
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
        validatePage(page, size);
        hrAuthorizer.requirePermission(principal, PermissionCodes.HR_ATTENDANCE_REVIEW);
        String normalizedSort = normalizeSort(sort);
        StringBuilder sql = new StringBuilder("""
                SELECT ae.id,
                       ae.employee_id,
                       ae.region_id,
                       ae.outlet_id,
                       ae.shift_assignment_id,
                       s.shift_date,
                       ae.event_type,
                       ae.event_time,
                       ae.source_system
                FROM hr.attendance_event ae
                JOIN hr.shift_assignment sa ON sa.id = ae.shift_assignment_id
                JOIN hr.shift_schedule s ON s.id = sa.shift_schedule_id
                WHERE 1 = 1
                """);
        MapSqlParameterSource parameters = params();
        if (employeeId != null) {
            sql.append("\n  AND ae.employee_id = :employeeId");
            parameters.addValue("employeeId", employeeId);
        }
        if (shiftAssignmentId != null) {
            sql.append("\n  AND ae.shift_assignment_id = :shiftAssignmentId");
            parameters.addValue("shiftAssignmentId", shiftAssignmentId);
        }
        if (regionId != null) {
            hrAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.HR_ATTENDANCE_REVIEW);
            sql.append("\n  AND ae.region_id = :regionId");
            parameters.addValue("regionId", regionId);
        }
        if (outletId != null) {
            hrAuthorizer.requireOutletPermission(principal, outletId, PermissionCodes.HR_ATTENDANCE_REVIEW);
            sql.append("\n  AND ae.outlet_id = :outletId");
            parameters.addValue("outletId", outletId);
        }
        if (fromDate != null) {
            sql.append("\n  AND s.shift_date >= :fromDate");
            parameters.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            sql.append("\n  AND s.shift_date <= :toDate");
            parameters.addValue("toDate", toDate);
        }
        applyAttendanceVisibilityScope(principal, regionId, outletId, sql, parameters);
        sql.append("\nORDER BY ae.event_time ").append(normalizedSort).append(", ae.id ").append(normalizedSort);
        sql.append("\nLIMIT :limit OFFSET :offset");
        parameters.addValue("limit", size + 1);
        parameters.addValue("offset", page * size);
        List<AttendanceEventListItemResponse> items = jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new AttendanceEventListItemResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("shift_assignment_id"),
                rs.getObject("shift_date", LocalDate.class),
                rs.getString("event_type"),
                instant(rs, "event_time"),
                rs.getString("source_system")
        ));
        return toPageResponse(items, page, size);
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
        List<ApprovedAttendanceResponse> items = jdbcTemplate.query("""
                SELECT aa.id AS approval_id,
                       sa.id AS shift_assignment_id,
                       sa.employee_id,
                       s.region_id,
                       s.outlet_id,
                       s.shift_date,
                       sa.attendance_status,
                       (EXTRACT(EPOCH FROM (MAX(CASE WHEN ae.event_type = 'CLOCK_OUT' THEN ae.event_time END)
                                        - MIN(CASE WHEN ae.event_type = 'CLOCK_IN' THEN ae.event_time END))) / 3600.0) AS work_hours,
                       GREATEST((EXTRACT(EPOCH FROM (MAX(CASE WHEN ae.event_type = 'CLOCK_OUT' THEN ae.event_time END)
                                                 - MIN(CASE WHEN ae.event_type = 'CLOCK_IN' THEN ae.event_time END))) / 3600.0)
                                - (EXTRACT(EPOCH FROM (CASE
                                        WHEN s.end_time > s.start_time THEN
                                            (s.shift_date + s.end_time) - (s.shift_date + s.start_time)
                                        ELSE
                                            ((s.shift_date + 1) + s.end_time) - (s.shift_date + s.start_time)
                                    END)) / 3600.0), 0) AS overtime_hours
                FROM hr.attendance_approval aa
                JOIN hr.shift_assignment sa ON sa.id = aa.shift_assignment_id
                JOIN hr.shift_schedule s ON s.id = sa.shift_schedule_id
                LEFT JOIN hr.attendance_event ae ON ae.shift_assignment_id = sa.id
                """ + whereClause + """
                GROUP BY aa.id, sa.id, sa.employee_id, s.region_id, s.outlet_id, s.shift_date, s.start_time, s.end_time, sa.attendance_status
                ORDER BY s.shift_date, sa.id
                """, params, (rs, rowNum) -> new ApprovedAttendanceResponse(
                rs.getLong("approval_id"),
                rs.getLong("shift_assignment_id"),
                rs.getLong("employee_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                null,
                rs.getObject("shift_date", LocalDate.class),
                rs.getString("attendance_status"),
                roundHours(rs.getBigDecimal("work_hours")),
                roundHours(rs.getBigDecimal("overtime_hours"))
        ));
        return attachContractIds(items);
    }

    private List<ApprovedAttendanceResponse> attachContractIds(List<ApprovedAttendanceResponse> items) {
        if (items.isEmpty()) {
            return items;
        }
        LocalDate minBusinessDate = items.stream()
                .map(ApprovedAttendanceResponse::businessDate)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate maxBusinessDate = items.stream()
                .map(ApprovedAttendanceResponse::businessDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
        List<Long> employeeIds = items.stream()
                .map(ApprovedAttendanceResponse::employeeId)
                .distinct()
                .toList();
        List<EffectiveContractResponse> contracts = masterJdbcTemplate.query("""
                SELECT id, employee_id, region_id, employment_type, salary_type, base_salary, tax_code, start_date, end_date
                FROM hr_master.employee_contract
                WHERE employee_id IN (:employeeIds)
                  AND contract_status = 'ACTIVE'
                  AND start_date <= :maxBusinessDate
                  AND (end_date IS NULL OR end_date >= :minBusinessDate)
                ORDER BY employee_id, start_date DESC, id DESC
                """, params(
                "employeeIds", employeeIds,
                "minBusinessDate", minBusinessDate,
                "maxBusinessDate", maxBusinessDate
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
        Map<Long, List<EffectiveContractResponse>> contractsByEmployeeId = new HashMap<>();
        for (EffectiveContractResponse contract : contracts) {
            contractsByEmployeeId.computeIfAbsent(contract.employeeId(), ignored -> new ArrayList<>()).add(contract);
        }
        return items.stream()
                .map(item -> new ApprovedAttendanceResponse(
                        item.approvalId(),
                        item.shiftAssignmentId(),
                        item.employeeId(),
                        item.regionId(),
                        item.outletId(),
                        selectActiveContractId(contractsByEmployeeId.get(item.employeeId()), item.businessDate()),
                        item.businessDate(),
                        item.attendanceStatus(),
                        item.workHours(),
                        item.overtimeHours()
                ))
                .toList();
    }

    private Long selectActiveContractId(List<EffectiveContractResponse> contracts, LocalDate businessDate) {
        if (contracts == null || contracts.isEmpty()) {
            return null;
        }
        for (EffectiveContractResponse contract : contracts) {
            boolean startsOnOrBefore = !contract.startDate().isAfter(businessDate);
            boolean endsOnOrAfter = contract.endDate() == null || !contract.endDate().isBefore(businessDate);
            if (startsOnOrBefore && endsOnOrAfter) {
                return contract.contractId();
            }
        }
        return null;
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
                """, params("id", id), rs -> rs.next() ? mapShiftAssignmentRecord(rs) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Shift assignment not found");
        }
        return response;
    }

    private ShiftAssignmentRecord requireShiftAssignmentForUpdate(Long id) {
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
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? mapShiftAssignmentRecord(rs) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Shift assignment not found");
        }
        return response;
    }

    private ShiftAssignmentRecord mapShiftAssignmentRecord(ResultSet rs) throws java.sql.SQLException {
        return new ShiftAssignmentRecord(
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
        );
    }

    private AttendanceAnalysis analyzeAttendance(ShiftAssignmentRecord assignment, List<AttendanceEventRecord> events) {
        if (events.isEmpty()) {
            return new AttendanceAnalysis("ABSENT", zeroHours(), zeroHours(), true, true, null);
        }
        AttendancePhase phase = AttendancePhase.EXPECT_CLOCK_IN;
        Instant lastEventTime = null;
        Instant clockIn = null;
        Instant clockOut = null;
        Instant breakStartedAt = null;
        Duration breakDuration = Duration.ZERO;
        for (AttendanceEventRecord event : events) {
            if (!isWithinAttendanceWindow(assignment, event.eventTime())) {
                return invalidAttendance("Attendance event time is outside the allowed shift window");
            }
            if (lastEventTime != null && !event.eventTime().isAfter(lastEventTime)) {
                return invalidAttendance("Attendance events must be strictly ordered by event time");
            }
            switch (event.eventType()) {
                case "CLOCK_IN" -> {
                    if (phase != AttendancePhase.EXPECT_CLOCK_IN) {
                        return invalidAttendance("Attendance events contain an invalid clock-in sequence");
                    }
                    clockIn = event.eventTime();
                    phase = AttendancePhase.EXPECT_BREAK_OR_CLOCK_OUT;
                }
                case "BREAK_START" -> {
                    if (phase != AttendancePhase.EXPECT_BREAK_OR_CLOCK_OUT) {
                        return invalidAttendance("Attendance events contain an invalid break-start sequence");
                    }
                    breakStartedAt = event.eventTime();
                    phase = AttendancePhase.EXPECT_BREAK_END;
                }
                case "BREAK_END" -> {
                    if (phase != AttendancePhase.EXPECT_BREAK_END || breakStartedAt == null) {
                        return invalidAttendance("Attendance events contain an invalid break-end sequence");
                    }
                    Duration currentBreak = Duration.between(breakStartedAt, event.eventTime());
                    if (currentBreak.isNegative() || currentBreak.isZero()) {
                        return invalidAttendance("Break duration must be greater than zero");
                    }
                    breakDuration = breakDuration.plus(currentBreak);
                    breakStartedAt = null;
                    phase = AttendancePhase.EXPECT_BREAK_OR_CLOCK_OUT;
                }
                case "CLOCK_OUT" -> {
                    if (phase != AttendancePhase.EXPECT_BREAK_OR_CLOCK_OUT || clockIn == null) {
                        return invalidAttendance("Attendance events contain an invalid clock-out sequence");
                    }
                    clockOut = event.eventTime();
                    phase = AttendancePhase.COMPLETE;
                }
                default -> {
                    return invalidAttendance("Attendance events contain an unsupported event type");
                }
            }
            lastEventTime = event.eventTime();
        }
        if (phase == AttendancePhase.EXPECT_CLOCK_IN) {
            return new AttendanceAnalysis("ABSENT", zeroHours(), zeroHours(), true, true, null);
        }
        if (phase != AttendancePhase.COMPLETE || clockIn == null || clockOut == null) {
            return new AttendanceAnalysis("PENDING", zeroHours(), zeroHours(), true, false, "Attendance record is incomplete");
        }
        Duration workDuration = Duration.between(clockIn, clockOut).minus(breakDuration);
        if (workDuration.isNegative() || workDuration.isZero()) {
            return invalidAttendance("Attendance work duration must be greater than zero");
        }
        Duration scheduledDuration = scheduledShiftDuration(assignment);
        BigDecimal workHours = toHours(workDuration);
        BigDecimal overtimeHours = toHours(workDuration.minus(scheduledDuration).isNegative()
                ? Duration.ZERO
                : workDuration.minus(scheduledDuration));
        Instant shiftStart = shiftStartInstant(assignment);
        String attendanceStatus = clockIn.isAfter(shiftStart.plus(LATE_GRACE_PERIOD)) ? "LATE" : "PRESENT";
        return new AttendanceAnalysis(attendanceStatus, workHours, overtimeHours, true, true, null);
    }

    private AttendanceAnalysis invalidAttendance(String validationError) {
        return new AttendanceAnalysis("PENDING", zeroHours(), zeroHours(), false, false, validationError);
    }

    private void validateAttendanceEventWindow(ShiftAssignmentRecord assignment, Instant eventTime) {
        if (!isWithinAttendanceWindow(assignment, eventTime)) {
            throw new ConflictException("Attendance event time is outside the allowed shift window");
        }
    }

    private boolean isWithinAttendanceWindow(ShiftAssignmentRecord assignment, Instant eventTime) {
        Instant earliest = shiftStartInstant(assignment).minus(ATTENDANCE_EARLY_WINDOW);
        Instant latest = shiftEndInstant(assignment).plus(ATTENDANCE_LATE_WINDOW);
        return !eventTime.isBefore(earliest) && !eventTime.isAfter(latest);
    }

    private void validateNextAttendanceEvent(List<AttendanceEventRecord> existingEvents, RecordAttendanceEventRequest request) {
        if (existingEvents.isEmpty()) {
            if (!"CLOCK_IN".equals(request.eventType())) {
                throw new ConflictException("The first attendance event must be CLOCK_IN");
            }
            return;
        }
        AttendanceEventRecord lastEvent = existingEvents.getLast();
        if (!request.eventTime().isAfter(lastEvent.eventTime())) {
            throw new ConflictException("Attendance events must be recorded in chronological order");
        }
        switch (lastEvent.eventType()) {
            case "CLOCK_IN", "BREAK_END" -> {
                if (!List.of("BREAK_START", "CLOCK_OUT").contains(request.eventType())) {
                    throw new ConflictException("Attendance event sequence is invalid after clock-in");
                }
            }
            case "BREAK_START" -> {
                if (!"BREAK_END".equals(request.eventType())) {
                    throw new ConflictException("Attendance event sequence is invalid during a break");
                }
            }
            case "CLOCK_OUT" -> throw new ConflictException("Attendance is already closed for this shift assignment");
            default -> throw new ConflictException("Attendance events contain an unsupported sequence");
        }
    }

    private List<AttendanceEventRecord> attendanceEventsForShiftAssignment(Long shiftAssignmentId) {
        return jdbcTemplate.query("""
                SELECT id, employee_id, region_id, outlet_id, shift_assignment_id, event_type, event_time, source_system, idempotency_key
                FROM hr.attendance_event
                WHERE shift_assignment_id = :shiftAssignmentId
                ORDER BY event_time, id
                """, params("shiftAssignmentId", shiftAssignmentId), (rs, rowNum) -> mapAttendanceEventRecord(rs));
    }

    /**
     * Batch-loads attendance events for multiple shift assignments in a single query.
     * This replaces the N+1 pattern where attendanceEventsForShiftAssignment() was
     * called individually per row in listAttendanceApprovals().
     */
    private Map<Long, List<AttendanceEventRecord>> batchLoadAttendanceEvents(List<Long> shiftAssignmentIds) {
        if (shiftAssignmentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AttendanceEventRecord>> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT id, employee_id, region_id, outlet_id, shift_assignment_id, event_type, event_time, source_system, idempotency_key
                FROM hr.attendance_event
                WHERE shift_assignment_id IN (:shiftAssignmentIds)
                ORDER BY shift_assignment_id, event_time, id
                """, params("shiftAssignmentIds", shiftAssignmentIds), (rs, rowNum) -> {
            long assignmentId = rs.getLong("shift_assignment_id");
            result.computeIfAbsent(assignmentId, k -> new ArrayList<>()).add(mapAttendanceEventRecord(rs));
            return null;
        });
        return result;
    }

    private Optional<AttendanceEventRecord> findAttendanceEventByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(jdbcTemplate.query("""
                SELECT id, employee_id, region_id, outlet_id, shift_assignment_id, event_type, event_time, source_system, idempotency_key
                FROM hr.attendance_event
                WHERE idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? mapAttendanceEventRecord(rs) : null));
    }

    private AttendanceEventRecord requireAttendanceEventRecord(Long id) {
        AttendanceEventRecord record = jdbcTemplate.query("""
                SELECT id, employee_id, region_id, outlet_id, shift_assignment_id, event_type, event_time, source_system, idempotency_key
                FROM hr.attendance_event
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? mapAttendanceEventRecord(rs) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Attendance event not found");
        }
        return record;
    }

    private AttendanceEventRecord mapAttendanceEventRecord(ResultSet rs) throws java.sql.SQLException {
        return new AttendanceEventRecord(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("shift_assignment_id"),
                rs.getString("event_type"),
                instant(rs, "event_time"),
                rs.getString("source_system"),
                rs.getString("idempotency_key")
        );
    }

    private AttendanceEventResponse toAttendanceEventResponse(AttendanceEventRecord record) {
        return new AttendanceEventResponse(
                record.id(),
                record.employeeId(),
                record.shiftAssignmentId(),
                record.eventType(),
                record.eventTime(),
                record.sourceSystem()
        );
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private void requireMatchingIdempotentAttendanceEvent(AttendanceEventRecord existingEvent, RecordAttendanceEventRequest request) {
        if (!matchesExistingAttendanceEvent(existingEvent, request)) {
            throw new ConflictException("Idempotency-Key cannot be reused with a different attendance event request");
        }
    }

    private boolean matchesExistingAttendanceEvent(AttendanceEventRecord existingEvent, RecordAttendanceEventRequest request) {
        return Objects.equals(existingEvent.employeeId(), request.employeeId())
                && Objects.equals(existingEvent.regionId(), request.regionId())
                && Objects.equals(existingEvent.outletId(), request.outletId())
                && Objects.equals(existingEvent.shiftAssignmentId(), request.shiftAssignmentId())
                && Objects.equals(existingEvent.eventType(), request.eventType())
                && Objects.equals(existingEvent.eventTime(), request.eventTime())
                && Objects.equals(existingEvent.sourceSystem(), request.sourceSystem());
    }

    private Instant shiftStartInstant(ShiftAssignmentRecord assignment) {
        return LocalDateTime.of(assignment.shiftDate(), assignment.startTime())
                .atZone(attendanceBusinessZone)
                .toInstant();
    }

    private Instant shiftEndInstant(ShiftAssignmentRecord assignment) {
        LocalDate shiftEndDate = assignment.endTime().isAfter(assignment.startTime())
                ? assignment.shiftDate()
                : assignment.shiftDate().plusDays(1);
        return LocalDateTime.of(shiftEndDate, assignment.endTime())
                .atZone(attendanceBusinessZone)
                .toInstant();
    }

    private Duration scheduledShiftDuration(ShiftAssignmentRecord assignment) {
        return Duration.between(shiftStartInstant(assignment), shiftEndInstant(assignment));
    }

    private BigDecimal toHours(Duration duration) {
        return BigDecimal.valueOf(duration.toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroHours() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal roundHours(BigDecimal value) {
        return value == null ? zeroHours() : value.setScale(2, RoundingMode.HALF_UP);
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
            AttendanceAnalysis computation,
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

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page cannot be negative");
        }
        if (size < 1) {
            throw new BadRequestException("Page size must be greater than 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size cannot exceed 200");
        }
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "DESC";
        }
        return switch (sort.trim().toUpperCase()) {
            case "ASC" -> "ASC";
            case "DESC" -> "DESC";
            default -> throw new BadRequestException("Sort must be either asc or desc");
        };
    }

    private void applyAttendanceVisibilityScope(
            FernPrincipal principal,
            Long regionId,
            Long outletId,
            StringBuilder sql,
            MapSqlParameterSource parameters
    ) {
        if (principal == null || ScopeAccess.isSystemScoped(principal) || regionId != null || outletId != null) {
            return;
        }
        ScopeRoots accessibleScope = ScopeAccess.accessibleScope(principal);
        List<Long> outletScopes = accessibleScope.outlets();
        List<Long> regionScopes = accessibleScope.regions();
        if (outletScopes.isEmpty() && regionScopes.isEmpty()) {
            throw new com.fern.platform.common.ForbiddenException("Attendance events are outside the current scope");
        }
        sql.append("\n  AND (");
        boolean appended = false;
        if (!outletScopes.isEmpty()) {
            sql.append("ae.outlet_id IN (:outletScopeIds)");
            parameters.addValue("outletScopeIds", outletScopes);
            appended = true;
        }
        if (!regionScopes.isEmpty()) {
            if (appended) {
                sql.append(" OR ");
            }
            sql.append("ae.region_id IN (:regionScopeIds)");
            parameters.addValue("regionScopeIds", regionScopes);
        }
        sql.append(')');
    }

    private <T> PageResponse<T> toPageResponse(List<T> items, int page, int size) {
        boolean hasMore = items.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(items.subList(0, size)) : items;
        return new PageResponse<>(pagedItems, page, size, hasMore);
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

    private record AttendanceEventRecord(
            Long id,
            Long employeeId,
            Long regionId,
            Long outletId,
            Long shiftAssignmentId,
            String eventType,
            Instant eventTime,
            String sourceSystem,
            String idempotencyKey
    ) {
    }

    private record AttendanceAnalysis(
            String attendanceStatus,
            BigDecimal workHours,
            BigDecimal overtimeHours,
            boolean valid,
            boolean approvable,
            String validationError
    ) {
    }

    private record ApprovalRow(Long id, String status, String comments, Long approvedByUserId, Instant approvedAt) {
    }

    private enum AttendancePhase {
        EXPECT_CLOCK_IN,
        EXPECT_BREAK_OR_CLOCK_OUT,
        EXPECT_BREAK_END,
        COMPLETE
    }
}
