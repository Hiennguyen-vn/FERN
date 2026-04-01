package com.fern.hrservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fern.hrservice.controller.HrReadController;
import com.fern.hrservice.controller.InternalHrController;
import com.fern.hrservice.dto.HrCommands.CreateAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateContractRequest;
import com.fern.hrservice.dto.HrCommands.CreateEmployeeRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftScheduleRequest;
import com.fern.hrservice.dto.HrCommands.RecordAttendanceEventRequest;
import com.fern.hrservice.service.HrService;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HrServiceIntegrationTest {
    private static final String TEST_SECRET = "hr-test-secret-key-012345678901234567890123456789";
    private static HttpServer orgServer;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensureOrgServerStarted();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.clients.org.base-url", () -> "http://localhost:" + orgServer.getAddress().getPort());
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> TEST_SECRET);
    }

    @Autowired
    private HrService hrService;

    @Autowired
    private HrReadController hrReadController;

    @Autowired
    private InternalHrController internalHrController;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private NamedParameterJdbcTemplate masterJdbcTemplate;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    private static void ensureOrgServerStarted() {
        if (orgServer != null) {
            return;
        }
        try {
            orgServer = HttpServer.create(new InetSocketAddress(0), 0);
            orgServer.createContext("/outlets", exchange -> {
                String path = exchange.getRequestURI().getPath();
                int status = 404;
                byte[] body = "{}".getBytes();
                if ("/outlets/201".equals(path)) {
                    status = 200;
                    body = """
                            {
                              "id": 201,
                              "regionId": 1
                            }
                            """.getBytes();
                } else if ("/outlets/202".equals(path)) {
                    status = 200;
                    body = """
                            {
                              "id": 202,
                              "regionId": 2
                            }
                            """.getBytes();
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(body);
                }
            });
            orgServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start HR org stub", exception);
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    hr.outbox_event,
                    hr.attendance_approval,
                    hr.attendance_event,
                    hr.shift_assignment,
                    hr.shift_schedule,
                    hr.employee_assignment
                RESTART IDENTITY CASCADE
                """);
        masterJdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    hr_master.employee_contract,
                    hr_master.employee_profile
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldRejectSpoofedAttendanceRoute() {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-001",
                "Alice",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        long scheduleId = hrService.createShiftSchedule(systemPrincipal, new CreateShiftScheduleRequest(
                2L,
                202L,
                LocalDate.of(2026, 3, 28),
                "Late Shift",
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                null
        )).id();
        long shiftAssignmentId = hrService.createShiftAssignment(systemPrincipal, new CreateShiftAssignmentRequest(
                scheduleId,
                employeeId,
                "CASHIER",
                null
        )).id();

        FernPrincipal restrictedPrincipal = outletPrincipal(PermissionCodes.HR_ATTENDANCE_WRITE, 201L);

        assertThatThrownBy(() -> hrService.recordAttendanceEvent(restrictedPrincipal, "attendance-route-mismatch", new RecordAttendanceEventRequest(
                employeeId,
                1L,
                201L,
                shiftAssignmentId,
                "CLOCK_IN",
                Instant.parse("2026-03-28T02:00:00Z"),
                "POS"
        ))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldRejectAssignmentRouteMismatch() {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_WRITE
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-003",
                "Carol",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();

        assertThatThrownBy(() -> hrService.createAssignment(systemPrincipal, new CreateAssignmentRequest(
                employeeId,
                999L,
                201L,
                "Cashier",
                LocalDate.of(2026, 3, 28),
                null,
                true,
                null
        ))).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldRejectShiftScheduleRouteMismatch() {
        FernPrincipal systemPrincipal = systemPrincipal(PermissionCodes.HR_SHIFT_WRITE);

        assertThatThrownBy(() -> hrService.createShiftSchedule(systemPrincipal, new CreateShiftScheduleRequest(
                999L,
                201L,
                LocalDate.of(2026, 3, 28),
                "Mismatch Shift",
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                null
        ))).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldCreateSingleApprovalAndOutboxEventOnConcurrentApprove() throws Exception {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE,
                PermissionCodes.HR_ATTENDANCE_REVIEW
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-002",
                "Bob",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        long scheduleId = hrService.createShiftSchedule(systemPrincipal, new CreateShiftScheduleRequest(
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                "Morning Shift",
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                null
        )).id();
        long shiftAssignmentId = hrService.createShiftAssignment(systemPrincipal, new CreateShiftAssignmentRequest(
                scheduleId,
                employeeId,
                "SERVER",
                null
        )).id();
        hrService.recordAttendanceEvent(systemPrincipal, "attendance-approve-clock-in", new RecordAttendanceEventRequest(
                employeeId,
                1L,
                201L,
                shiftAssignmentId,
                "CLOCK_IN",
                Instant.parse("2026-03-28T02:00:00Z"),
                "POS"
        ));
        hrService.recordAttendanceEvent(systemPrincipal, "attendance-approve-clock-out", new RecordAttendanceEventRequest(
                employeeId,
                1L,
                201L,
                shiftAssignmentId,
                "CLOCK_OUT",
                Instant.parse("2026-03-28T10:00:00Z"),
                "POS"
        ));

        FernPrincipal reviewerPrincipal = outletPrincipal(PermissionCodes.HR_ATTENDANCE_REVIEW, 201L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                hrService.reviewAttendance(reviewerPrincipal, shiftAssignmentId, "APPROVED", "approved");
                return null;
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                hrService.reviewAttendance(reviewerPrincipal, shiftAssignmentId, "APPROVED", "approved");
                return null;
            });
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        Integer approvalCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM hr.attendance_approval WHERE shift_assignment_id = %d
                """.formatted(shiftAssignmentId), Integer.class);
        Integer outboxCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM hr.outbox_event WHERE event_type = 'attendance.approved'
                """, Integer.class);
        String approvalStatus = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT status FROM hr.attendance_approval WHERE shift_assignment_id = %d
                """.formatted(shiftAssignmentId), String.class);

        assertThat(approvalCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
        assertThat(approvalStatus).isEqualTo("APPROVED");
    }

    @Test
    void shouldFindApprovedAttendanceWithContractResolvedFromMasterDatasource() {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_CONTRACT_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE,
                PermissionCodes.HR_ATTENDANCE_REVIEW
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-PAYROLL-001",
                "Payroll Attendance",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        long contractId = hrService.createContract(systemPrincipal, new CreateContractRequest(
                employeeId,
                "FULL_TIME",
                "MONTHLY",
                BigDecimal.valueOf(1800),
                1L,
                "TAX-PAYROLL",
                "ACTIVE",
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        long scheduleId = hrService.createShiftSchedule(systemPrincipal, new CreateShiftScheduleRequest(
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                "Payroll Shift",
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                null
        )).id();
        long shiftAssignmentId = hrService.createShiftAssignment(systemPrincipal, new CreateShiftAssignmentRequest(
                scheduleId,
                employeeId,
                "SERVER",
                null
        )).id();

        hrService.recordAttendanceEvent(systemPrincipal, "payroll-attendance-clock-in", new RecordAttendanceEventRequest(
                employeeId,
                1L,
                201L,
                shiftAssignmentId,
                "CLOCK_IN",
                Instant.parse("2026-03-28T02:00:00Z"),
                "POS"
        ));
        hrService.recordAttendanceEvent(systemPrincipal, "payroll-attendance-clock-out", new RecordAttendanceEventRequest(
                employeeId,
                1L,
                201L,
                shiftAssignmentId,
                "CLOCK_OUT",
                Instant.parse("2026-03-28T10:00:00Z"),
                "POS"
        ));
        hrService.reviewAttendance(outletPrincipal(PermissionCodes.HR_ATTENDANCE_REVIEW, 201L), shiftAssignmentId, "APPROVED", "approved");

        var approvedAttendance = hrService.findApprovedAttendance(1L, LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 28));

        assertThat(approvedAttendance).singleElement().satisfies(item -> {
            assertThat(item.employeeId()).isEqualTo(employeeId);
            assertThat(item.contractId()).isEqualTo(contractId);
            assertThat(item.attendanceStatus()).isEqualTo("PRESENT");
            assertThat(item.workHours()).isEqualByComparingTo("8.00");
        });
    }

    @Test
    void shouldReplayConcurrentAttendanceEventWithSameIdempotencyKey() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-001",
                "Attendance Replay",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Replay Shift"
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return hrService.recordAttendanceEvent(
                        principal,
                        "attendance-same-key",
                        attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z")
                ).id();
            });
            Future<Long> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return hrService.recordAttendanceEvent(
                        principal,
                        "attendance-same-key",
                        attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z")
                ).id();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get()).isEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }

        Integer eventCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*) FROM hr.attendance_event WHERE shift_assignment_id = %d
                """.formatted(fixture.shiftAssignmentId()), Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidContractDateRangeOverHttp() throws Exception {
        mockMvc.perform(post("/employee-contracts")
                        .header("Authorization", bearer(Set.of(PermissionCodes.HR_CONTRACT_WRITE), List.of(), List.of(), true))
                        .contentType("application/json")
                        .content("""
                                {
                                  "employeeId": 1,
                                  "employmentType": "FULL_TIME",
                                  "salaryType": "MONTHLY",
                                  "baseSalary": 1500.00,
                                  "regionId": 1,
                                  "taxCode": "TAX-001",
                                  "contractStatus": "ACTIVE",
                                  "startDate": "2026-03-31",
                                  "endDate": "2026-03-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.dateRangeValid").value("endDate must be on or after startDate"));
    }

    @Test
    void shouldRejectInvalidAssignmentDateRangeOverHttp() throws Exception {
        mockMvc.perform(post("/employee-assignments")
                        .header("Authorization", bearer(Set.of(PermissionCodes.HR_SHIFT_WRITE), List.of(), List.of(), true))
                        .contentType("application/json")
                        .content("""
                                {
                                  "employeeId": 1,
                                  "regionId": 1,
                                  "outletId": 201,
                                  "positionTitle": "Cashier",
                                  "startDate": "2026-03-31",
                                  "endDate": "2026-03-01",
                                  "primaryAssignment": true,
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.dateRangeValid").value("endDate must be on or after startDate"));
    }

    @Test
    void shouldAllowOnlyOneConcurrentOverlappingContract() throws Exception {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_CONTRACT_WRITE
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-CONFLICT-001",
                "Overlap Contract",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> executeConcurrentContractCreate(systemPrincipal, employeeId, ready, start));
            Future<Boolean> second = executor.submit(() -> executeConcurrentContractCreate(systemPrincipal, employeeId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successCount = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Integer rowCount = masterJdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM hr_master.employee_contract
                WHERE employee_id = ?
                """, Integer.class, employeeId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyOneConcurrentConflictingShiftAssignment() throws Exception {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_WRITE
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-SHIFT-001",
                "Shift Conflict",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        long firstScheduleId = hrService.createShiftSchedule(systemPrincipal, new CreateShiftScheduleRequest(
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                "Morning A",
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                null
        )).id();
        long secondScheduleId = hrService.createShiftSchedule(systemPrincipal, new CreateShiftScheduleRequest(
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                "Morning B",
                LocalTime.of(13, 0),
                LocalTime.of(21, 0),
                null
        )).id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> executeConcurrentShiftAssignmentCreate(systemPrincipal, employeeId, firstScheduleId, ready, start));
            Future<Boolean> second = executor.submit(() -> executeConcurrentShiftAssignmentCreate(systemPrincipal, employeeId, secondScheduleId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successCount = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Integer rowCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM hr.shift_assignment
                WHERE employee_id = ?
                """, Integer.class, employeeId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void shouldRejectConcurrentAttendanceReplayWhenIdempotencyKeyTargetsDifferentShiftAssignments() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture firstFixture = createAttendanceFixture(
                principal,
                "EMP-ATT-REPLAY-001",
                "Attendance Replay One",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Replay Key Shift One"
        );
        AttendanceFixture secondFixture = createAttendanceFixture(
                principal,
                "EMP-ATT-REPLAY-002",
                "Attendance Replay Two",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                "Replay Key Shift Two"
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return hrService.recordAttendanceEvent(
                            principal,
                            "attendance-shared-conflict",
                            attendanceRequest(firstFixture, "CLOCK_IN", "2026-03-28T02:00:00Z")
                    ).id();
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            Future<Object> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return hrService.recordAttendanceEvent(
                            principal,
                            "attendance-shared-conflict",
                            attendanceRequest(secondFixture, "CLOCK_IN", "2026-03-28T03:00:00Z")
                    ).id();
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
            Throwable failure = results.stream()
                    .filter(Throwable.class::isInstance)
                    .map(Throwable.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(failure).isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Idempotency-Key cannot be reused with a different attendance event request");
        } finally {
            executor.shutdownNow();
        }

        Integer idempotencyCount = jdbcTemplate.getJdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM hr.attendance_event
                WHERE idempotency_key = 'attendance-shared-conflict'
                """, Integer.class);
        assertThat(idempotencyCount).isEqualTo(1);
    }

    @Test
    void shouldRejectAttendanceEventReplayWhenPayloadDiffers() {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-002",
                "Attendance Replay Conflict",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Replay Conflict Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-different-payload", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z"));

        assertThatThrownBy(() -> hrService.recordAttendanceEvent(
                principal,
                "attendance-different-payload",
                attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:01:00Z")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Idempotency-Key cannot be reused with a different attendance event request");
    }

    @Test
    void shouldRejectClockOutBeforeClockIn() {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-003",
                "Clock Out First",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Sequence Shift"
        );

        assertThatThrownBy(() -> hrService.recordAttendanceEvent(
                principal,
                "attendance-clock-out-first",
                attendanceRequest(fixture, "CLOCK_OUT", "2026-03-28T10:00:00Z")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("first attendance event must be CLOCK_IN");
    }

    @Test
    void shouldRejectDuplicateClockInSequence() {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-004",
                "Duplicate Clock In",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Duplicate Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-duplicate-1", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z"));

        assertThatThrownBy(() -> hrService.recordAttendanceEvent(
                principal,
                "attendance-duplicate-2",
                attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:05:00Z")
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("invalid after clock-in");
    }

    @Test
    void shouldApproveAttendanceWithBreakDeduction() {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE,
                PermissionCodes.HR_ATTENDANCE_REVIEW
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-005",
                "Break Deduction",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Break Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-break-clock-in", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:10:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-break-start", attendanceRequest(fixture, "BREAK_START", "2026-03-28T05:00:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-break-end", attendanceRequest(fixture, "BREAK_END", "2026-03-28T05:30:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-break-clock-out", attendanceRequest(fixture, "CLOCK_OUT", "2026-03-28T10:10:00Z"));

        var response = hrService.reviewAttendance(outletPrincipal(PermissionCodes.HR_ATTENDANCE_REVIEW, 201L), fixture.shiftAssignmentId(), "APPROVED", "approved");

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.attendanceStatus()).isEqualTo("LATE");
        assertThat(response.workHours()).isEqualByComparingTo("7.50");
        assertThat(response.overtimeHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldApproveOvernightShiftAttendance() {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE,
                PermissionCodes.HR_ATTENDANCE_REVIEW
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-006",
                "Overnight Attendance",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                "Overnight Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-overnight-clock-in", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T15:00:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-overnight-clock-out", attendanceRequest(fixture, "CLOCK_OUT", "2026-03-28T23:00:00Z"));

        var response = hrService.reviewAttendance(outletPrincipal(PermissionCodes.HR_ATTENDANCE_REVIEW, 201L), fixture.shiftAssignmentId(), "APPROVED", "approved");

        assertThat(response.attendanceStatus()).isEqualTo("PRESENT");
        assertThat(response.workHours()).isEqualByComparingTo("8.00");
        assertThat(response.overtimeHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectApproveWhenAttendanceIsIncomplete() {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE,
                PermissionCodes.HR_ATTENDANCE_REVIEW
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-007",
                "Incomplete Attendance",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Incomplete Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-incomplete-clock-in", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z"));

        assertThatThrownBy(() -> hrService.reviewAttendance(
                outletPrincipal(PermissionCodes.HR_ATTENDANCE_REVIEW, 201L),
                fixture.shiftAssignmentId(),
                "APPROVED",
                "approved"
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("Attendance record is incomplete");
    }

    @Test
    void shouldRequireAttendanceEventIdempotencyHeaderOverHttp() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-008",
                "Http Idempotency",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "HTTP Shift"
        );

        mockMvc.perform(post("/attendance-events")
                        .header("Authorization", bearer(Set.of(PermissionCodes.HR_ATTENDANCE_WRITE), List.of(), List.of(201L), false))
                        .contentType("application/json")
                        .content(attendanceEventJson(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void shouldReturnBadRequestForInvalidAttendanceEventTypeOverHttp() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-009",
                "Http Validation",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "HTTP Validation Shift"
        );

        mockMvc.perform(post("/attendance-events")
                        .header("Authorization", bearer(Set.of(PermissionCodes.HR_ATTENDANCE_WRITE), List.of(), List.of(201L), false))
                        .header("Idempotency-Key", "attendance-http-invalid")
                        .contentType("application/json")
                        .content(attendanceEventJson(fixture, "INVALID", "2026-03-28T02:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.eventType").value("must be one of CLOCK_IN, CLOCK_OUT, BREAK_START, or BREAK_END"));
    }

    @Test
    void shouldListAttendanceEventsWithPaginationOverHttp() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-010",
                "Attendance List",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "List Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-list-clock-in", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-list-clock-out", attendanceRequest(fixture, "CLOCK_OUT", "2026-03-28T10:00:00Z"));

        mockMvc.perform(get("/attendance-events")
                        .header("Authorization", bearer(Set.of(PermissionCodes.HR_ATTENDANCE_REVIEW), List.of(), List.of(201L), false))
                        .param("outletId", "201")
                        .param("shiftAssignmentId", fixture.shiftAssignmentId().toString())
                        .param("fromDate", "2026-03-28")
                        .param("toDate", "2026-03-28")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].eventType").value("CLOCK_IN"))
                .andExpect(jsonPath("$.items[0].shiftAssignmentId").value(fixture.shiftAssignmentId()));
    }

    @Test
    void shouldListAttendanceEventsForDescendantRouteWhenAccessibleScopeIsExpanded() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-010A",
                "Attendance Descendant Scope",
                2L,
                202L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Descendant Scope Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-descendant-clock-in", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-descendant-clock-out", attendanceRequest(fixture, "CLOCK_OUT", "2026-03-28T10:00:00Z"));

        mockMvc.perform(get("/attendance-events")
                        .header("Authorization", bearer(
                                Set.of(PermissionCodes.HR_ATTENDANCE_REVIEW),
                                new ScopeRoots(false, List.of(1L), List.of()),
                                new ScopeRoots(false, List.of(1L, 2L), List.of(202L))
                        ))
                        .param("shiftAssignmentId", fixture.shiftAssignmentId().toString())
                        .param("fromDate", "2026-03-28")
                        .param("toDate", "2026-03-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shiftAssignmentId").value(fixture.shiftAssignmentId()))
                .andExpect(jsonPath("$.items[0].regionId").value(2))
                .andExpect(jsonPath("$.items[0].outletId").value(202));
    }

    @Test
    void shouldBrowseEmployeesAndContractsOverHttp() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_CONTRACT_READ,
                PermissionCodes.HR_CONTRACT_WRITE
        );
        long employeeId = hrService.createEmployee(principal, new CreateEmployeeRequest(
                "EMP-BROWSE-001",
                "Browse Target",
                null,
                null,
                "browse@fern.local",
                "0909000000",
                "ACTIVE",
                LocalDate.of(2026, 1, 10),
                null
        )).id();
        hrService.createContract(principal, new CreateContractRequest(
                employeeId,
                "FULL_TIME",
                "MONTHLY",
                new BigDecimal("15000000"),
                1L,
                "TAX-BROWSE-001",
                "ACTIVE",
                LocalDate.of(2026, 1, 10),
                null
        ));

        String authorization = bearer(
                Set.of(PermissionCodes.HR_EMPLOYEE_READ, PermissionCodes.HR_CONTRACT_READ),
                List.of(),
                List.of(),
                true
        );

        mockMvc.perform(get("/employees")
                        .header("Authorization", authorization)
                        .param("search", "Browse")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].employeeCode").value("EMP-BROWSE-001"))
                .andExpect(jsonPath("$.items[0].fullName").value("Browse Target"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));

        mockMvc.perform(get("/employee-contracts")
                        .header("Authorization", authorization)
                        .param("employeeId", Long.toString(employeeId))
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].employeeId").value(employeeId))
                .andExpect(jsonPath("$.items[0].contractStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    @Tag("security-gap")
    void shouldRejectReadingShiftAssignmentAndApprovalOutsideOutletScopeById() throws Exception {
        FernPrincipal principal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_SHIFT_READ,
                PermissionCodes.HR_SHIFT_WRITE,
                PermissionCodes.HR_ATTENDANCE_WRITE,
                PermissionCodes.HR_ATTENDANCE_REVIEW
        );
        AttendanceFixture fixture = createAttendanceFixture(
                principal,
                "EMP-ATT-011",
                "Scoped Attendance",
                1L,
                201L,
                LocalDate.of(2026, 3, 28),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Scoped Shift"
        );

        hrService.recordAttendanceEvent(principal, "attendance-scope-clock-in", attendanceRequest(fixture, "CLOCK_IN", "2026-03-28T02:00:00Z"));
        hrService.recordAttendanceEvent(principal, "attendance-scope-clock-out", attendanceRequest(fixture, "CLOCK_OUT", "2026-03-28T10:00:00Z"));
        hrService.reviewAttendance(
                outletPrincipal(PermissionCodes.HR_ATTENDANCE_REVIEW, 201L),
                fixture.shiftAssignmentId(),
                "APPROVED",
                "approved"
        );

        String inScopeBearer = bearer(
                Set.of(PermissionCodes.HR_SHIFT_READ, PermissionCodes.HR_ATTENDANCE_REVIEW),
                List.of(),
                List.of(201L),
                false
        );
        String outOfScopeBearer = bearer(
                Set.of(PermissionCodes.HR_SHIFT_READ, PermissionCodes.HR_ATTENDANCE_REVIEW),
                List.of(),
                List.of(202L),
                false
        );

        mockMvc.perform(get("/shift-assignments/{id}", fixture.shiftAssignmentId())
                        .header("Authorization", inScopeBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fixture.shiftAssignmentId()));

        mockMvc.perform(get("/shift-assignments/{id}", fixture.shiftAssignmentId())
                        .header("Authorization", outOfScopeBearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/attendance-approvals/{shiftAssignmentId}", fixture.shiftAssignmentId())
                        .header("Authorization", inScopeBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftAssignmentId").value(fixture.shiftAssignmentId()));

        mockMvc.perform(get("/attendance-approvals/{shiftAssignmentId}", fixture.shiftAssignmentId())
                        .header("Authorization", outOfScopeBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldMaskContractDetailWithoutDetailPermission() {
        long employeeId = seedEmployeeWithContract();
        FernPrincipal principal = new FernPrincipal(
                10L,
                "hr-reader",
                Set.of("hr"),
                Set.of(PermissionCodes.HR_CONTRACT_READ),
                new ScopeRoots(false, List.of(1L), List.of()),
                1L,
                1L,
                UUID.randomUUID().toString()
        );

        var responses = hrReadController.listContracts(principal, employeeId);

        assertThat(responses).singleElement().satisfies(contract -> {
            assertThat(contract.regionId()).isEqualTo(1L);
            assertThat(contract.baseSalary()).isNull();
            assertThat(contract.taxCode()).isNull();
        });
    }

    @Test
    void shouldExposeContractDetailWithDetailPermission() {
        long employeeId = seedEmployeeWithContract();
        FernPrincipal principal = new FernPrincipal(
                11L,
                "hr-detail-reader",
                Set.of("hr"),
                Set.of(PermissionCodes.HR_CONTRACT_READ, PermissionCodes.HR_CONTRACT_DETAIL_READ),
                new ScopeRoots(false, List.of(1L), List.of()),
                1L,
                1L,
                UUID.randomUUID().toString()
        );

        var responses = hrReadController.listContracts(principal, employeeId);

        assertThat(responses).singleElement().satisfies(contract -> {
            assertThat(contract.baseSalary()).isNotNull();
            assertThat(contract.taxCode()).isEqualTo("TAX-001");
        });
    }

    @Test
    void shouldKeepInternalEffectiveContractsUnmasked() {
        long employeeId = seedEmployeeWithContract();
        FernPrincipal servicePrincipal = new FernPrincipal(
                null,
                "finance-service",
                Set.of(),
                Set.of(PermissionCodes.HR_INTERNAL_READ),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                UUID.randomUUID().toString(),
                FernPrincipalType.SERVICE
        );

        var responses = internalHrController.effectiveContracts(
                servicePrincipal,
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertThat(responses).anySatisfy(contract -> {
            assertThat(contract.employeeId()).isEqualTo(employeeId);
            assertThat(contract.baseSalary()).isNotNull();
            assertThat(contract.taxCode()).isEqualTo("TAX-001");
        });
    }

    private AttendanceFixture createAttendanceFixture(
            FernPrincipal principal,
            String employeeCode,
            String employeeName,
            Long regionId,
            Long outletId,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            String shiftName
    ) {
        long employeeId = hrService.createEmployee(principal, new CreateEmployeeRequest(
                employeeCode,
                employeeName,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        long scheduleId = hrService.createShiftSchedule(principal, new CreateShiftScheduleRequest(
                regionId,
                outletId,
                shiftDate,
                shiftName,
                startTime,
                endTime,
                null
        )).id();
        long shiftAssignmentId = hrService.createShiftAssignment(principal, new CreateShiftAssignmentRequest(
                scheduleId,
                employeeId,
                "CASHIER",
                null
        )).id();
        return new AttendanceFixture(employeeId, shiftAssignmentId, regionId, outletId);
    }

    private RecordAttendanceEventRequest attendanceRequest(AttendanceFixture fixture, String eventType, String eventTime) {
        return new RecordAttendanceEventRequest(
                fixture.employeeId(),
                fixture.regionId(),
                fixture.outletId(),
                fixture.shiftAssignmentId(),
                eventType,
                Instant.parse(eventTime),
                "POS"
        );
    }

    private String attendanceEventJson(AttendanceFixture fixture, String eventType, String eventTime) {
        return """
                {
                  "employeeId": %d,
                  "regionId": %d,
                  "outletId": %d,
                  "shiftAssignmentId": %d,
                  "eventType": "%s",
                  "eventTime": "%s",
                  "sourceSystem": "POS"
                }
                """.formatted(
                fixture.employeeId(),
                fixture.regionId(),
                fixture.outletId(),
                fixture.shiftAssignmentId(),
                eventType,
                eventTime
        );
    }

    private boolean executeConcurrentContractCreate(
            FernPrincipal principal,
            long employeeId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            hrService.createContract(principal, new CreateContractRequest(
                    employeeId,
                    "FULL_TIME",
                    "MONTHLY",
                    BigDecimal.valueOf(1500),
                    1L,
                    "TAX-001",
                    "ACTIVE",
                    LocalDate.of(2026, 3, 1),
                    null
            ));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private boolean executeConcurrentShiftAssignmentCreate(
            FernPrincipal principal,
            long employeeId,
            long shiftScheduleId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            hrService.createShiftAssignment(principal, new CreateShiftAssignmentRequest(
                    shiftScheduleId,
                    employeeId,
                    "CASHIER",
                    null
            ));
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private FernPrincipal systemPrincipal(String... permissions) {
        return new FernPrincipal(
                1L,
                "system",
                Set.of("hr"),
                Set.of(permissions),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                UUID.randomUUID().toString()
        );
    }

    private FernPrincipal outletPrincipal(String permission, long outletId) {
        return new FernPrincipal(
                2L,
                "outlet-manager",
                Set.of("outlet_manager"),
                Set.of(permission),
                new ScopeRoots(false, List.of(), List.of(outletId)),
                1L,
                1L,
                UUID.randomUUID().toString()
        );
    }

    private long seedEmployeeWithContract() {
        FernPrincipal systemPrincipal = systemPrincipal(
                PermissionCodes.HR_EMPLOYEE_READ,
                PermissionCodes.HR_EMPLOYEE_WRITE,
                PermissionCodes.HR_CONTRACT_READ,
                PermissionCodes.HR_CONTRACT_WRITE,
                PermissionCodes.HR_CONTRACT_DETAIL_READ
        );
        long employeeId = hrService.createEmployee(systemPrincipal, new CreateEmployeeRequest(
                "EMP-CONTRACT-001",
                "Daisy",
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null
        )).id();
        hrService.createContract(systemPrincipal, new CreateContractRequest(
                employeeId,
                "FULL_TIME",
                "MONTHLY",
                java.math.BigDecimal.valueOf(1500),
                1L,
                "TAX-001",
                "ACTIVE",
                LocalDate.of(2026, 1, 1),
                null
        ));
        return employeeId;
    }

    private String bearer(Set<String> permissions, List<Long> regions, List<Long> outlets, boolean systemScoped) {
        return bearer(permissions, new ScopeRoots(systemScoped, regions, outlets), new ScopeRoots(systemScoped, regions, outlets));
    }

    private String bearer(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret(TEST_SECRET);
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, java.time.Clock.systemUTC());
        String token = jwtService.encode(new FernJwtClaims(
                1L,
                "hr-http-tester",
                Set.of("hr"),
                permissions,
                scopeRoots,
                accessibleScope,
                1L,
                1L,
                "hr-test-jti-" + permissions.hashCode() + "-" + scopeRoots.hashCode() + "-" + accessibleScope.hashCode(),
                Instant.now(),
                Instant.now().plusSeconds(900),
                com.fern.platform.common.FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("hr-service")
        ), jwtService.accessTokenTtl());
        return "Bearer " + token;
    }

    private record AttendanceFixture(Long employeeId, Long shiftAssignmentId, Long regionId, Long outletId) {
    }
}
