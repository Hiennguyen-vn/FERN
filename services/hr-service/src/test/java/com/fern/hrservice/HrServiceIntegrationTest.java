package com.fern.hrservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.hrservice.dto.HrCommands.CreateAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateEmployeeRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftAssignmentRequest;
import com.fern.hrservice.dto.HrCommands.CreateShiftScheduleRequest;
import com.fern.hrservice.dto.HrCommands.RecordAttendanceEventRequest;
import com.fern.hrservice.service.HrService;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
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
    private NamedParameterJdbcTemplate jdbcTemplate;

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

        assertThatThrownBy(() -> hrService.recordAttendanceEvent(restrictedPrincipal, new RecordAttendanceEventRequest(
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
        hrService.recordAttendanceEvent(systemPrincipal, new RecordAttendanceEventRequest(
                employeeId,
                1L,
                201L,
                shiftAssignmentId,
                "CLOCK_IN",
                Instant.parse("2026-03-28T02:00:00Z"),
                "POS"
        ));
        hrService.recordAttendanceEvent(systemPrincipal, new RecordAttendanceEventRequest(
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
}
