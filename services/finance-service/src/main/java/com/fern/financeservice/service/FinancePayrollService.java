package com.fern.financeservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollRunRequest;
import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.dto.FinanceCommands.PutNumberingRuleRequest;
import com.fern.financeservice.dto.FinanceCommands.PutSystemPolicyRequest;
import com.fern.financeservice.dto.FinanceResponses.NumberingRuleResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollAllocationResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollEmployeeResultResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollLineResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollPeriodResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollAllocation;
import com.fern.platform.contracts.PayrollCalculatedEvent.PayrollCalculatedEmployee;
import com.fern.platform.contracts.PayrollPostedEvent;
import com.fern.platform.contracts.PayrollPostedEvent.PayrollExpenseLink;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class FinancePayrollService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final FinanceAuthorizer financeAuthorizer;
    private final FinanceAuditService financeAuditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient restClient;
    private final FernServiceTokenSupport serviceTokenSupport;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final String hrBaseUrl;

    public FinancePayrollService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            FinanceAuthorizer financeAuthorizer,
            FinanceAuditService financeAuditService,
            ObjectMapper objectMapper,
            Clock clock,
            RestClient restClient,
            FernServiceTokenSupport serviceTokenSupport,
            SnowflakeIdGenerator snowflakeIdGenerator,
            @Value("${fern.clients.hr-base-url}") String hrBaseUrl
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.financeAuthorizer = financeAuthorizer;
        this.financeAuditService = financeAuditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.restClient = restClient;
        this.serviceTokenSupport = serviceTokenSupport;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.hrBaseUrl = hrBaseUrl;
    }

    @Transactional
    public PayrollPeriodResponse createPayrollPeriod(FernPrincipal principal, CreatePayrollPeriodRequest request) {
        financeAuthorizer.requireRegionPermission(principal, request.regionId(), PermissionCodes.FINANCE_PAYROLL_PREPARE);
        ensureNoOverlappingPeriods(request.regionId(), request.startDate(), request.endDate());
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO finance.payroll_period (
                    region_id, reference_code, name, start_date, end_date, pay_date, status, note, created_at, updated_at
                ) VALUES (
                    :regionId, :referenceCode, :name, :startDate, :endDate, :payDate, 'DRAFT', :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "regionId", request.regionId(),
                "referenceCode", nextDocumentNumber("PAYROLL_PERIOD"),
                "name", request.name(),
                "startDate", request.startDate(),
                "endDate", request.endDate(),
                "payDate", request.payDate(),
                "note", request.note()
        ));
        PayrollPeriodResponse response = getPayrollPeriod(principal, id);
        financeAuditService.publish("finance.payroll.period.created", principal, request.regionId(), null, "CREATE", "PAYROLL_PERIOD", id.toString(), null, response, Map.of());
        return response;
    }

    @Transactional
    public PayrollRunResponse createPayrollRun(FernPrincipal principal, CreatePayrollRunRequest request) {
        PayrollPeriodRecord period = requirePayrollPeriodRecord(request.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_PREPARE);
        Long runId = insertForId(jdbcTemplate, """
                INSERT INTO finance.payroll_run (
                    payroll_period_id, run_code, run_date, status, total_amount, processed_by_user_id, note, created_at, updated_at
                ) VALUES (
                    :payrollPeriodId, :runCode, :runDate, 'DRAFT', 0, :processedByUserId, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "payrollPeriodId", request.payrollPeriodId(),
                "runCode", nextDocumentNumber("PAYROLL_RUN"),
                "runDate", request.runDate() == null ? LocalDate.now(clock) : request.runDate(),
                "processedByUserId", principal == null ? null : principal.userId(),
                "note", request.note()
        ));
        recalculateRun(runId, period);
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeAuditService.publish("finance.payroll.draft.created", principal, period.regionId(), null, "CREATE", "PAYROLL_RUN", runId.toString(), null, response, Map.of());
        return response;
    }

    @Transactional
    public PayrollRunResponse submitPayrollRun(FernPrincipal principal, Long runId, String note) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = requirePayrollPeriodRecord(run.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_PREPARE);
        if (!"DRAFT".equals(run.status()) && !"REJECTED".equals(run.status())) {
            throw new BadRequestException("Only draft or rejected payroll runs can be submitted");
        }
        recalculateRun(runId, period);
        int updated = jdbcTemplate.update("""
                UPDATE finance.payroll_run
                SET status = 'SUBMITTED',
                    submitted_by_user_id = :submittedByUserId,
                    submitted_at = CURRENT_TIMESTAMP,
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status IN ('DRAFT', 'REJECTED')
                """, params(
                "submittedByUserId", principal == null ? null : principal.userId(),
                "note", note,
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only draft or rejected payroll runs can be submitted");
        }
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeAuditService.publish("finance.payroll.submitted", principal, period.regionId(), null, "SUBMIT", "PAYROLL_RUN", runId.toString(), run, response, Map.of());
        return response;
    }

    @Transactional
    public PayrollRunResponse approvePayrollRun(FernPrincipal principal, Long runId, String note) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = requirePayrollPeriodRecord(run.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_APPROVE);
        if (!"SUBMITTED".equals(run.status())) {
            throw new BadRequestException("Only submitted payroll runs can be approved");
        }
        int updated = jdbcTemplate.update("""
                UPDATE finance.payroll_run
                SET status = 'APPROVED',
                    approved_by_user_id = :approvedByUserId,
                    approved_at = CURRENT_TIMESTAMP,
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'SUBMITTED'
                """, params(
                "approvedByUserId", principal == null ? null : principal.userId(),
                "note", note,
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only submitted payroll runs can be approved");
        }
        PayrollRunResponse response = getPayrollRun(principal, runId);
        emitPayrollCalculated(period, response, principal);
        financeAuditService.publish("finance.payroll.approved", principal, period.regionId(), null, "APPROVE", "PAYROLL_RUN", runId.toString(), run, response, Map.of());
        return response;
    }

    @Transactional
    public PayrollRunResponse rejectPayrollRun(FernPrincipal principal, Long runId, String note) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = requirePayrollPeriodRecord(run.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_APPROVE);
        if (!"SUBMITTED".equals(run.status())) {
            throw new BadRequestException("Only submitted payroll runs can be rejected");
        }
        int updated = jdbcTemplate.update("""
                UPDATE finance.payroll_run
                SET status = 'REJECTED',
                    rejected_by_user_id = :rejectedByUserId,
                    rejected_at = CURRENT_TIMESTAMP,
                    rejection_reason = :rejectionReason,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'SUBMITTED'
                """, params(
                "rejectedByUserId", principal == null ? null : principal.userId(),
                "rejectionReason", note,
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only submitted payroll runs can be rejected");
        }
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeAuditService.publish("finance.payroll.rejected", principal, period.regionId(), null, "REJECT", "PAYROLL_RUN", runId.toString(), run, response, Map.of("reason", note));
        return response;
    }

    @Transactional
    public PayrollRunResponse markPayrollPaid(FernPrincipal principal, Long runId, MarkPaidRequest request) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = requirePayrollPeriodRecord(run.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_PAY);
        if (!"APPROVED".equals(run.status())) {
            throw new BadRequestException("Only approved payroll runs can be marked paid");
        }
        int updated = jdbcTemplate.update("""
                UPDATE finance.payroll_run
                SET status = 'PAID',
                    payment_ref = :paymentRef,
                    paid_by_user_id = :paidByUserId,
                    paid_at = CURRENT_TIMESTAMP,
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'APPROVED'
                """, params(
                "paymentRef", request.paymentReference(),
                "paidByUserId", principal == null ? null : principal.userId(),
                "note", request.note(),
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only approved payroll runs can be marked paid");
        }
        List<PayrollEmployeeResultResponse> employees = queryPayrollEmployees(runId);
        List<PayrollExpenseLink> links = new ArrayList<>();
        for (PayrollEmployeeResultResponse employee : employees) {
            for (PayrollAllocationResponse allocation : employee.allocations()) {
                Long expenseRecordId = insertForId(jdbcTemplate, """
                        INSERT INTO finance.expense_record (
                            region_id, outlet_id, employee_id, reference_code, expense_time, amount, source_type, status, note,
                            submitted_by_user_id, approved_by_user_id, created_at, updated_at, posted_at
                        ) VALUES (
                            :regionId, :outletId, :employeeId, :referenceCode, :expenseTime, :amount, 'PAYROLL', 'POSTED', :note,
                            :submittedByUserId, :approvedByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """, params(
                        "regionId", period.regionId(),
                        "outletId", allocation.outletId(),
                        "employeeId", employee.employeeId(),
                        "referenceCode", nextDocumentNumber("PAYROLL_EXPENSE"),
                        "expenseTime", clock.instant(),
                        "amount", allocation.allocatedAmount(),
                        "note", "Payroll run " + runId + " paid",
                        "submittedByUserId", principal == null ? null : principal.userId(),
                        "approvedByUserId", principal == null ? null : principal.userId()
                ));
                jdbcTemplate.update("""
                        INSERT INTO finance.expense_payroll (expense_record_id, payroll_run_id)
                        VALUES (:expenseRecordId, :payrollRunId)
                        ON CONFLICT (expense_record_id) DO NOTHING
                        """, params("expenseRecordId", expenseRecordId, "payrollRunId", runId));
                emitExpensePosted(
                        expenseRecordId,
                        period.regionId(),
                        allocation.outletId(),
                        employee.employeeId(),
                        runId,
                        run.runDate(),
                        "PAYROLL",
                        allocation.allocatedAmount(),
                        currentCorrelationId(),
                        "PAYROLL_RUN",
                        runId.toString()
                );
                links.add(new PayrollExpenseLink(expenseRecordId, employee.employeeId(), allocation.outletId(), allocation.allocatedAmount()));
            }
        }
        jdbcTemplate.update("""
                UPDATE finance.payroll_employee_result
                SET payment_status = 'PAID', updated_at = CURRENT_TIMESTAMP
                WHERE payroll_run_id = :payrollRunId
                """, params("payrollRunId", runId));
        PayrollRunResponse response = getPayrollRun(principal, runId);
        emitPayrollPosted(period, response, principal, request.paymentReference(), links);
        financeAuditService.publish("finance.payroll.paid", principal, period.regionId(), null, "MARK_PAID", "PAYROLL_RUN", runId.toString(), run, response, Map.of("paymentReference", request.paymentReference()));
        return response;
    }

    public List<PayrollPeriodResponse> listPayrollPeriods(FernPrincipal principal, Long regionId) {
        if (regionId != null) {
            financeAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.FINANCE_PAYROLL_READ);
            return jdbcTemplate.query("""
                    SELECT id, region_id, reference_code, name, start_date, end_date, pay_date, status, note
                    FROM finance.payroll_period
                    WHERE region_id = :regionId
                    ORDER BY start_date DESC, id DESC
                    """, params("regionId", regionId), (rs, rowNum) -> mapPayrollPeriod(rs));
        }
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_PAYROLL_READ);
        return jdbcTemplate.query("""
                SELECT id, region_id, reference_code, name, start_date, end_date, pay_date, status, note
                FROM finance.payroll_period
                ORDER BY start_date DESC, id DESC
                """, params(), (rs, rowNum) -> mapPayrollPeriod(rs));
    }

    public PayrollPeriodResponse getPayrollPeriod(FernPrincipal principal, Long id) {
        PayrollPeriodRecord record = requirePayrollPeriodRecord(id);
        financeAuthorizer.requireRegionPermission(principal, record.regionId(), PermissionCodes.FINANCE_PAYROLL_READ);
        return jdbcTemplate.query("""
                SELECT id, region_id, reference_code, name, start_date, end_date, pay_date, status, note
                FROM finance.payroll_period
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? mapPayrollPeriod(rs) : null);
    }

    public List<PayrollRunResponse> listPayrollRuns(FernPrincipal principal, Long regionId) {
        if (regionId != null) {
            financeAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.FINANCE_PAYROLL_READ);
            return jdbcTemplate.query("""
                    SELECT pr.id
                    FROM finance.payroll_run pr
                    JOIN finance.payroll_period pp ON pp.id = pr.payroll_period_id
                    WHERE pp.region_id = :regionId
                    ORDER BY pr.run_date DESC, pr.id DESC
                    """, params("regionId", regionId), (rs, rowNum) -> getPayrollRun(principal, rs.getLong("id")));
        }
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_PAYROLL_READ);
        return jdbcTemplate.query("""
                SELECT pr.id
                FROM finance.payroll_run pr
                JOIN finance.payroll_period pp ON pp.id = pr.payroll_period_id
                ORDER BY pr.run_date DESC, pr.id DESC
                """, params(), (rs, rowNum) -> getPayrollRun(principal, rs.getLong("id")));
    }

    public PayrollRunResponse getPayrollRun(FernPrincipal principal, Long id) {
        PayrollRunRecord record = requirePayrollRunRecord(id);
        PayrollPeriodRecord period = requirePayrollPeriodRecord(record.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_READ);
        return jdbcTemplate.query("""
                SELECT id, payroll_period_id, run_code, run_date, status, total_amount, payment_ref, note, submitted_at, approved_at, paid_at
                FROM finance.payroll_run
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new PayrollRunResponse(
                rs.getLong("id"),
                rs.getLong("payroll_period_id"),
                rs.getString("run_code"),
                rs.getObject("run_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("payment_ref"),
                rs.getString("note"),
                instant(rs, "submitted_at"),
                instant(rs, "approved_at"),
                instant(rs, "paid_at"),
                queryPayrollEmployees(id)
        ) : null);
    }

    @Transactional("masterTransactionManager")
    public NumberingRuleResponse putNumberingRule(FernPrincipal principal, String documentType, PutNumberingRuleRequest request) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_WRITE);
        masterJdbcTemplate.update("""
                INSERT INTO config.document_numbering_rule (
                    document_type, prefix, region_id, outlet_id, next_number, reset_period, format_pattern, is_active, updated_at
                ) VALUES (
                    :documentType, :prefix, :regionId, :outletId, :nextNumber, :resetPeriod, :formatPattern, :active, CURRENT_TIMESTAMP
                )
                ON CONFLICT (document_type) DO UPDATE
                SET prefix = EXCLUDED.prefix,
                    region_id = EXCLUDED.region_id,
                    outlet_id = EXCLUDED.outlet_id,
                    next_number = EXCLUDED.next_number,
                    reset_period = EXCLUDED.reset_period,
                    format_pattern = EXCLUDED.format_pattern,
                    is_active = EXCLUDED.is_active,
                    updated_at = CURRENT_TIMESTAMP
                """, params(
                "documentType", documentType,
                "prefix", request.prefix(),
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "nextNumber", request.nextNumber() == null ? 1L : request.nextNumber(),
                "resetPeriod", request.resetPeriod() == null ? "NEVER" : request.resetPeriod(),
                "formatPattern", request.formatPattern(),
                "active", request.active() == null ? Boolean.TRUE : request.active()
        ));
        return getNumberingRule(principal, documentType);
    }

    public NumberingRuleResponse getNumberingRule(FernPrincipal principal, String documentType) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_READ);
        NumberingRuleResponse response = masterJdbcTemplate.query("""
                SELECT id, document_type, prefix, region_id, outlet_id, next_number, reset_period, format_pattern, is_active
                FROM config.document_numbering_rule
                WHERE document_type = :documentType
                """, params("documentType", documentType), rs -> rs.next() ? new NumberingRuleResponse(
                rs.getLong("id"),
                rs.getString("document_type"),
                rs.getString("prefix"),
                nullableLong(rs, "region_id"),
                nullableLong(rs, "outlet_id"),
                rs.getLong("next_number"),
                rs.getString("reset_period"),
                rs.getString("format_pattern"),
                rs.getBoolean("is_active")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Numbering rule not found");
        }
        return response;
    }

    @Transactional("masterTransactionManager")
    public SystemPolicyResponse putSystemPolicy(FernPrincipal principal, String policyKey, PutSystemPolicyRequest request) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_WRITE);
        masterJdbcTemplate.update("""
                INSERT INTO config.system_policy (
                    policy_key, policy_value, description, updated_by_user_id, updated_at
                ) VALUES (
                    :policyKey, CAST(:policyValue AS jsonb), :description, :updatedByUserId, CURRENT_TIMESTAMP
                )
                ON CONFLICT (policy_key) DO UPDATE
                SET policy_value = EXCLUDED.policy_value,
                    description = EXCLUDED.description,
                    updated_by_user_id = EXCLUDED.updated_by_user_id,
                    updated_at = CURRENT_TIMESTAMP
                """, params(
                "policyKey", policyKey,
                "policyValue", request.policyValue().toString(),
                "description", request.description(),
                "updatedByUserId", principal == null ? null : principal.userId()
        ));
        return getSystemPolicy(principal, policyKey);
    }

    public SystemPolicyResponse getSystemPolicy(FernPrincipal principal, String policyKey) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_READ);
        SystemPolicyResponse response = masterJdbcTemplate.query("""
                SELECT policy_key, policy_value::text AS policy_value, description
                FROM config.system_policy
                WHERE policy_key = :policyKey
                """, params("policyKey", policyKey), rs -> rs.next() ? new SystemPolicyResponse(
                rs.getString("policy_key"),
                readTree(rs.getString("policy_value")),
                rs.getString("description")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("System policy not found");
        }
        return response;
    }

    private void recalculateRun(Long runId, PayrollPeriodRecord period) {
        deleteExistingRunArtifacts(runId);
        List<EffectiveContract> contracts = fetchEffectiveContracts(period.regionId(), period.startDate(), period.endDate());
        List<ApprovedAttendance> attendance = fetchApprovedAttendance(period.regionId(), period.startDate(), period.endDate());
        Map<Long, List<EffectiveContract>> contractsByEmployee = new HashMap<>();
        for (EffectiveContract contract : contracts) {
            contractsByEmployee.computeIfAbsent(contract.employeeId(), ignored -> new ArrayList<>()).add(contract);
            insertForId(jdbcTemplate, """
                    INSERT INTO finance.payroll_contract_snapshot (
                        payroll_run_id, employee_id, contract_id, employment_type, salary_type, base_salary, tax_code, start_date, end_date, created_at
                    ) VALUES (
                        :payrollRunId, :employeeId, :contractId, :employmentType, :salaryType, :baseSalary, :taxCode, :startDate, :endDate, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "payrollRunId", runId,
                    "employeeId", contract.employeeId(),
                    "contractId", contract.contractId(),
                    "employmentType", contract.employmentType(),
                    "salaryType", contract.salaryType(),
                    "baseSalary", contract.baseSalary(),
                    "taxCode", contract.taxCode(),
                    "startDate", contract.startDate(),
                    "endDate", contract.endDate()
            ));
        }

        Map<Long, List<ApprovedAttendance>> attendanceByEmployee = new LinkedHashMap<>();
        for (ApprovedAttendance item : attendance) {
            attendanceByEmployee.computeIfAbsent(item.employeeId(), ignored -> new ArrayList<>()).add(item);
        }

        JsonNode overtimePolicy = readPolicyValue("payroll.overtime");
        JsonNode allowancePolicy = readPolicyValue("payroll.allowance");
        JsonNode deductionPolicy = readPolicyValue("payroll.deduction");
        JsonNode taxPolicy = readPolicyValue("payroll.tax");
        JsonNode roundingPolicy = readPolicyValue("payroll.rounding");
        int scale = roundingPolicy.path("scale").asInt(2);
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);

        for (Map.Entry<Long, List<ApprovedAttendance>> entry : attendanceByEmployee.entrySet()) {
            Long employeeId = entry.getKey();
            List<ApprovedAttendance> employeeAttendance = entry.getValue();
            List<EffectiveContract> employeeContracts = contractsByEmployee.getOrDefault(employeeId, List.of());
            if (employeeContracts.isEmpty()) {
                continue;
            }
            employeeContracts = new ArrayList<>(employeeContracts);
            employeeContracts.sort(Comparator.comparing(EffectiveContract::startDate));
            PayrollEmployeeComputation computation = computeEmployeePayroll(period, employeeAttendance, employeeContracts, scale, overtimePolicy, allowancePolicy, deductionPolicy, taxPolicy);
            Long resultId = insertForId(jdbcTemplate, """
                    INSERT INTO finance.payroll_employee_result (
                        payroll_run_id, employee_id, contract_id, outlet_id, gross_pay, deduction_amount, tax_amount, net_pay,
                        payment_status, work_days, work_hours, overtime_hours, exception_message, created_at, updated_at
                    ) VALUES (
                        :payrollRunId, :employeeId, :contractId, :outletId, :grossPay, :deductionAmount, :taxAmount, :netPay,
                        'UNPAID', :workDays, :workHours, :overtimeHours, :exceptionMessage, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "payrollRunId", runId,
                    "employeeId", employeeId,
                    "contractId", computation.primaryContractId(),
                    "outletId", computation.primaryOutletId(),
                    "grossPay", computation.grossPay(),
                    "deductionAmount", computation.deductionAmount(),
                    "taxAmount", computation.taxAmount(),
                    "netPay", computation.netPay(),
                    "workDays", computation.workDays(),
                    "workHours", computation.workHours(),
                    "overtimeHours", computation.overtimeHours(),
                    "exceptionMessage", computation.exceptionMessage()
            ));
            for (PayrollLine line : computation.lines()) {
                insertForId(jdbcTemplate, """
                        INSERT INTO finance.payroll_result_line (
                            payroll_employee_result_id, line_type, description, amount, created_at
                        ) VALUES (
                            :payrollEmployeeResultId, :lineType, :description, :amount, CURRENT_TIMESTAMP
                        )
                        """, params(
                        "payrollEmployeeResultId", resultId,
                        "lineType", line.lineType(),
                        "description", line.description(),
                        "amount", line.amount()
                ));
            }
            for (OutletAllocation allocation : computation.allocations()) {
                insertForId(jdbcTemplate, """
                        INSERT INTO finance.payroll_result_allocation (
                            payroll_employee_result_id, outlet_id, work_hours, allocated_amount, created_at
                        ) VALUES (
                            :payrollEmployeeResultId, :outletId, :workHours, :allocatedAmount, CURRENT_TIMESTAMP
                        )
                        """, params(
                        "payrollEmployeeResultId", resultId,
                        "outletId", allocation.outletId(),
                        "workHours", allocation.workHours(),
                        "allocatedAmount", allocation.allocatedAmount()
                ));
            }
            for (ApprovedAttendance item : employeeAttendance) {
                insertForId(jdbcTemplate, """
                        INSERT INTO finance.payroll_attendance_snapshot (
                            payroll_run_id, payroll_employee_result_id, approval_id, shift_assignment_id, employee_id, outlet_id, contract_id,
                            business_date, attendance_status, work_hours, overtime_hours, created_at
                        ) VALUES (
                            :payrollRunId, :payrollEmployeeResultId, :approvalId, :shiftAssignmentId, :employeeId, :outletId, :contractId,
                            :businessDate, :attendanceStatus, :workHours, :overtimeHours, CURRENT_TIMESTAMP
                        )
                        """, params(
                        "payrollRunId", runId,
                        "payrollEmployeeResultId", resultId,
                        "approvalId", item.approvalId(),
                        "shiftAssignmentId", item.shiftAssignmentId(),
                        "employeeId", item.employeeId(),
                        "outletId", item.outletId(),
                        "contractId", selectContract(employeeContracts, item.businessDate()).contractId(),
                        "businessDate", item.businessDate(),
                        "attendanceStatus", item.attendanceStatus(),
                        "workHours", item.workHours(),
                        "overtimeHours", item.overtimeHours()
                ));
            }
            totalAmount = totalAmount.add(computation.netPay()).setScale(scale, RoundingMode.HALF_UP);
        }

        jdbcTemplate.update("""
                UPDATE finance.payroll_run
                SET total_amount = :totalAmount, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("totalAmount", totalAmount, "id", runId));
    }

    private PayrollEmployeeComputation computeEmployeePayroll(
            PayrollPeriodRecord period,
            List<ApprovedAttendance> attendance,
            List<EffectiveContract> contracts,
            int scale,
            JsonNode overtimePolicy,
            JsonNode allowancePolicy,
            JsonNode deductionPolicy,
            JsonNode taxPolicy
    ) {
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalOvertime = BigDecimal.ZERO;
        BigDecimal totalWorkHours = BigDecimal.ZERO;
        BigDecimal totalOvertimeHours = BigDecimal.ZERO;
        Map<Long, BigDecimal> outletHours = new LinkedHashMap<>();
        Map<Long, LocalDate> workedDays = new LinkedHashMap<>();

        for (ApprovedAttendance item : attendance) {
            EffectiveContract contract = selectContract(contracts, item.businessDate());
            BigDecimal base = basePayForDay(contract, period, item.workHours());
            BigDecimal overtime = overtimePay(contract, period, item.overtimeHours(), overtimePolicy);
            totalBase = totalBase.add(base);
            totalOvertime = totalOvertime.add(overtime);
            totalWorkHours = totalWorkHours.add(item.workHours());
            totalOvertimeHours = totalOvertimeHours.add(item.overtimeHours());
            outletHours.merge(item.outletId(), item.workHours().max(BigDecimal.ZERO), BigDecimal::add);
            workedDays.put(item.businessDate().toEpochDay(), item.businessDate());
        }

        BigDecimal allowance = BigDecimal.valueOf(workedDays.size())
                .multiply(BigDecimal.valueOf(allowancePolicy.path("mealPerWorkDay").asDouble(0) + allowancePolicy.path("transportPerWorkDay").asDouble(0)));
        long lateCount = attendance.stream().filter(item -> "LATE".equals(item.attendanceStatus())).count();
        BigDecimal deduction = BigDecimal.valueOf(lateCount).multiply(BigDecimal.valueOf(deductionPolicy.path("latePenaltyPerCount").asDouble(0)));
        BigDecimal taxable = totalBase.add(totalOvertime).add(allowance).subtract(deduction);
        BigDecimal tax = taxable.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(taxPolicy.path("rate").asDouble(0))).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal gross = totalBase.add(totalOvertime).add(allowance).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(deduction).subtract(tax).setScale(scale, RoundingMode.HALF_UP);

        List<PayrollLine> lines = List.of(
                new PayrollLine("BASE", "Base salary", totalBase.setScale(scale, RoundingMode.HALF_UP)),
                new PayrollLine("OVERTIME", "Overtime pay", totalOvertime.setScale(scale, RoundingMode.HALF_UP)),
                new PayrollLine("ALLOWANCE", "Policy allowance", allowance.setScale(scale, RoundingMode.HALF_UP)),
                new PayrollLine("DEDUCTION", "Policy deduction", deduction.setScale(scale, RoundingMode.HALF_UP).negate()),
                new PayrollLine("TAX", "Policy tax", tax.setScale(scale, RoundingMode.HALF_UP).negate()),
                new PayrollLine("NET", "Net pay", net)
        );
        List<OutletAllocation> allocations = new ArrayList<>();
        BigDecimal denominator = totalWorkHours.compareTo(BigDecimal.ZERO) > 0 ? totalWorkHours : BigDecimal.ONE;
        for (Map.Entry<Long, BigDecimal> entry : outletHours.entrySet()) {
            BigDecimal ratio = entry.getValue().divide(denominator, 8, RoundingMode.HALF_UP);
            allocations.add(new OutletAllocation(
                    entry.getKey(),
                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                    net.multiply(ratio).setScale(scale, RoundingMode.HALF_UP)
            ));
        }
        EffectiveContract primary = selectContract(contracts, attendance.get(0).businessDate());
        return new PayrollEmployeeComputation(
                primary.contractId(),
                allocations.isEmpty() ? null : allocations.get(0).outletId(),
                gross,
                deduction.setScale(scale, RoundingMode.HALF_UP),
                tax,
                net,
                BigDecimal.valueOf(workedDays.size()).setScale(2, RoundingMode.HALF_UP),
                totalWorkHours.setScale(2, RoundingMode.HALF_UP),
                totalOvertimeHours.setScale(2, RoundingMode.HALF_UP),
                null,
                lines,
                allocations
        );
    }

    private BigDecimal basePayForDay(EffectiveContract contract, PayrollPeriodRecord period, BigDecimal workHours) {
        return switch (contract.salaryType()) {
            case "MONTHLY" -> {
                long activeDays = period.startDate().until(period.endDate().plusDays(1), ChronoUnit.DAYS);
                BigDecimal dailyRate = contract.baseSalary().divide(BigDecimal.valueOf(Math.max(activeDays, 1L)), 8, RoundingMode.HALF_UP);
                yield dailyRate;
            }
            case "DAILY" -> contract.baseSalary();
            case "HOURLY" -> contract.baseSalary().multiply(workHours);
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal overtimePay(EffectiveContract contract, PayrollPeriodRecord period, BigDecimal overtimeHours, JsonNode overtimePolicy) {
        if (overtimeHours.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal hourlyRate = switch (contract.salaryType()) {
            case "MONTHLY" -> {
                long activeDays = period.startDate().until(period.endDate().plusDays(1), ChronoUnit.DAYS);
                yield contract.baseSalary().divide(BigDecimal.valueOf(Math.max(activeDays, 1L) * 8L), 8, RoundingMode.HALF_UP);
            }
            case "DAILY" -> contract.baseSalary().divide(BigDecimal.valueOf(8), 8, RoundingMode.HALF_UP);
            case "HOURLY" -> contract.baseSalary();
            default -> BigDecimal.ZERO;
        };
        BigDecimal multiplier = BigDecimal.valueOf(overtimePolicy.path("defaultMultiplier").asDouble(1.5d));
        return hourlyRate.multiply(overtimeHours).multiply(multiplier);
    }

    private EffectiveContract selectContract(List<EffectiveContract> contracts, LocalDate businessDate) {
        return contracts.stream()
                .filter(contract -> !contract.startDate().isAfter(businessDate))
                .filter(contract -> contract.endDate() == null || !contract.endDate().isBefore(businessDate))
                .max(Comparator.comparing(EffectiveContract::startDate))
                .orElse(contracts.get(contracts.size() - 1));
    }

    private void ensureNoOverlappingPeriods(Long regionId, LocalDate startDate, LocalDate endDate) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM finance.payroll_period
                WHERE region_id = :regionId
                  AND end_date >= :startDate
                  AND :endDate >= start_date
                  AND status <> 'CANCELLED'
                """, params("regionId", regionId, "startDate", startDate, "endDate", endDate), Integer.class);
        if (count != null && count > 0) {
            throw new BadRequestException("Payroll period overlaps an existing period");
        }
    }

    private void deleteExistingRunArtifacts(Long runId) {
        jdbcTemplate.update("DELETE FROM finance.payroll_contract_snapshot WHERE payroll_run_id = :payrollRunId", params("payrollRunId", runId));
        jdbcTemplate.update("DELETE FROM finance.payroll_attendance_snapshot WHERE payroll_run_id = :payrollRunId", params("payrollRunId", runId));
        jdbcTemplate.update("""
                DELETE FROM finance.payroll_result_line
                WHERE payroll_employee_result_id IN (
                    SELECT id FROM finance.payroll_employee_result WHERE payroll_run_id = :payrollRunId
                )
                """, params("payrollRunId", runId));
        jdbcTemplate.update("""
                DELETE FROM finance.payroll_result_allocation
                WHERE payroll_employee_result_id IN (
                    SELECT id FROM finance.payroll_employee_result WHERE payroll_run_id = :payrollRunId
                )
                """, params("payrollRunId", runId));
        jdbcTemplate.update("DELETE FROM finance.payroll_employee_result WHERE payroll_run_id = :payrollRunId", params("payrollRunId", runId));
    }

    private List<PayrollEmployeeResultResponse> queryPayrollEmployees(Long runId) {
        List<PayrollEmployeeResultResponse> employees = jdbcTemplate.query("""
                SELECT id, employee_id, contract_id, outlet_id, gross_pay, deduction_amount, tax_amount, net_pay,
                       work_days, work_hours, overtime_hours, payment_status, exception_message
                FROM finance.payroll_employee_result
                WHERE payroll_run_id = :payrollRunId
                ORDER BY employee_id, id
                """, params("payrollRunId", runId), (rs, rowNum) -> new PayrollEmployeeResultResponse(
                rs.getLong("id"),
                rs.getLong("employee_id"),
                nullableLong(rs, "contract_id"),
                nullableLong(rs, "outlet_id"),
                rs.getBigDecimal("gross_pay"),
                rs.getBigDecimal("deduction_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("net_pay"),
                rs.getBigDecimal("work_days"),
                rs.getBigDecimal("work_hours"),
                rs.getBigDecimal("overtime_hours"),
                rs.getString("payment_status"),
                rs.getString("exception_message"),
                queryLines(rs.getLong("id")),
                queryAllocations(rs.getLong("id"))
        ));
        return employees;
    }

    private List<PayrollLineResponse> queryLines(Long payrollEmployeeResultId) {
        return jdbcTemplate.query("""
                SELECT id, line_type, description, amount
                FROM finance.payroll_result_line
                WHERE payroll_employee_result_id = :payrollEmployeeResultId
                ORDER BY id
                """, params("payrollEmployeeResultId", payrollEmployeeResultId), (rs, rowNum) -> new PayrollLineResponse(
                rs.getLong("id"),
                rs.getString("line_type"),
                rs.getString("description"),
                rs.getBigDecimal("amount")
        ));
    }

    private List<PayrollAllocationResponse> queryAllocations(Long payrollEmployeeResultId) {
        return jdbcTemplate.query("""
                SELECT id, outlet_id, work_hours, allocated_amount
                FROM finance.payroll_result_allocation
                WHERE payroll_employee_result_id = :payrollEmployeeResultId
                ORDER BY id
                """, params("payrollEmployeeResultId", payrollEmployeeResultId), (rs, rowNum) -> new PayrollAllocationResponse(
                rs.getLong("id"),
                rs.getLong("outlet_id"),
                rs.getBigDecimal("work_hours"),
                rs.getBigDecimal("allocated_amount")
        ));
    }

    private PayrollPeriodRecord requirePayrollPeriodRecord(Long id) {
        PayrollPeriodRecord record = jdbcTemplate.query("""
                SELECT id, region_id, start_date, end_date, pay_date, status
                FROM finance.payroll_period
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new PayrollPeriodRecord(
                rs.getLong("id"),
                rs.getLong("region_id"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getObject("pay_date", LocalDate.class),
                rs.getString("status")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Payroll period not found");
        }
        return record;
    }

    private PayrollRunRecord requirePayrollRunRecord(Long id) {
        PayrollRunRecord record = jdbcTemplate.query("""
                SELECT id, payroll_period_id, run_date, status, total_amount
                FROM finance.payroll_run
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new PayrollRunRecord(
                rs.getLong("id"),
                rs.getLong("payroll_period_id"),
                rs.getObject("run_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Payroll run not found");
        }
        return record;
    }

    private List<EffectiveContract> fetchEffectiveContracts(Long regionId, LocalDate startDate, LocalDate endDate) {
        try {
            EffectiveContract[] response = restClient.get()
                    .uri(hrBaseUrl + "/internal/hr/effective-contracts?regionId=" + regionId + "&startDate=" + startDate + "&endDate=" + endDate)
                    .headers(headers -> applyInternalHeaders(headers, Set.of(PermissionCodes.HR_INTERNAL_READ)))
                    .retrieve()
                    .body(EffectiveContract[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientException exception) {
            throw new BadRequestException("Unable to fetch effective contracts from HR: " + exception.getMessage());
        }
    }

    private List<ApprovedAttendance> fetchApprovedAttendance(Long regionId, LocalDate startDate, LocalDate endDate) {
        try {
            ApprovedAttendance[] response = restClient.get()
                    .uri(hrBaseUrl + "/internal/hr/approved-attendance?regionId=" + regionId + "&startDate=" + startDate + "&endDate=" + endDate)
                    .headers(headers -> applyInternalHeaders(headers, Set.of(PermissionCodes.HR_INTERNAL_READ)))
                    .retrieve()
                    .body(ApprovedAttendance[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientException exception) {
            throw new BadRequestException("Unable to fetch approved attendance from HR: " + exception.getMessage());
        }
    }

    private void applyInternalHeaders(org.springframework.http.HttpHeaders headers, Collection<String> permissions) {
        headers.set(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenSupport.issueToken("finance-service", permissions));
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        String correlationId = attributes.getRequest().getHeader(CorrelationId.HEADER);
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
        String actorUserId = attributes.getRequest().getHeader(FernRequestHeaders.ACTOR_USER_ID);
        if (actorUserId != null) {
            headers.set(FernRequestHeaders.ACTOR_USER_ID, actorUserId);
        }
    }

    private String nextDocumentNumber(String documentType) {
        DocumentNumberAllocation allocation = masterJdbcTemplate.query("""
                UPDATE config.document_numbering_rule
                SET next_number = next_number + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE document_type = :documentType
                  AND is_active = TRUE
                RETURNING prefix, next_number - 1 AS allocated_number
                """, params("documentType", documentType), rs -> rs.next() ? new DocumentNumberAllocation(
                rs.getString("prefix"),
                rs.getLong("allocated_number")
        ) : null);
        if (allocation == null) {
            return documentType + "-" + snowflakeIdGenerator.nextId();
        }
        String prefix = allocation.prefix() == null ? documentType : allocation.prefix();
        return prefix + "-" + String.format("%06d", allocation.allocatedNumber());
    }

    private JsonNode readPolicyValue(String policyKey) {
        try {
            return masterJdbcTemplate.query("""
                    SELECT policy_value::text AS policy_value
                    FROM config.system_policy
                    WHERE policy_key = :policyKey
                    """, params("policyKey", policyKey), rs -> rs.next() ? readTree(rs.getString("policy_value")) : objectMapper.createObjectNode());
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private void emitPayrollCalculated(PayrollPeriodRecord period, PayrollRunResponse run, FernPrincipal principal) {
        List<PayrollCalculatedEmployee> employees = run.employees().stream()
                .map(item -> new PayrollCalculatedEmployee(item.employeeId(), item.outletId(), item.grossPay(), item.deductionAmount(), item.taxAmount(), item.netPay()))
                .toList();
        List<PayrollAllocation> allocations = run.employees().stream()
                .flatMap(item -> item.allocations().stream().map(allocation -> new PayrollAllocation(item.employeeId(), allocation.outletId(), allocation.workHours(), allocation.allocatedAmount())))
                .toList();
        PayrollCalculatedEvent event = new PayrollCalculatedEvent(
                UUID.randomUUID().toString(),
                "payroll.calculated",
                clock.instant(),
                "finance-service",
                currentCorrelationId(),
                UUID.randomUUID().toString(),
                run.id(),
                run.payrollPeriodId(),
                period.regionId(),
                run.runDate(),
                run.totalAmount(),
                principal == null ? null : principal.userId(),
                employees,
                allocations
        );
        enqueueOutbox("PAYROLL_RUN", run.id().toString(), "payroll.calculated", period.regionId().toString(), event);
    }

    private void emitPayrollPosted(
            PayrollPeriodRecord period,
            PayrollRunResponse run,
            FernPrincipal principal,
            String paymentReference,
            List<PayrollExpenseLink> links
    ) {
        PayrollPostedEvent event = new PayrollPostedEvent(
                UUID.randomUUID().toString(),
                "payroll.posted",
                clock.instant(),
                "finance-service",
                currentCorrelationId(),
                UUID.randomUUID().toString(),
                run.id(),
                run.payrollPeriodId(),
                period.regionId(),
                run.runDate(),
                run.totalAmount(),
                paymentReference,
                principal == null ? null : principal.userId(),
                links
        );
        enqueueOutbox("PAYROLL_RUN", run.id().toString(), "payroll.posted", period.regionId().toString(), event);
    }

    private void emitExpensePosted(
            Long expenseRecordId,
            Long regionId,
            Long outletId,
            Long employeeId,
            Long payrollRunId,
            LocalDate businessDate,
            String sourceType,
            BigDecimal amount,
            String correlationId,
            String sourceReferenceType,
            String sourceReferenceId
    ) {
        ExpensePostedEvent event = new ExpensePostedEvent(
                UUID.randomUUID().toString(),
                "finance.expense.posted",
                clock.instant(),
                "finance-service",
                correlationId,
                UUID.randomUUID().toString(),
                expenseRecordId,
                regionId,
                outletId,
                employeeId,
                payrollRunId,
                businessDate,
                sourceType,
                amount,
                sourceReferenceType,
                sourceReferenceId
        );
        enqueueOutbox("EXPENSE_RECORD", expenseRecordId.toString(), "finance.expense.posted", regionId.toString(), event);
    }

    private void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        jdbcTemplate.update("""
                INSERT INTO finance.outbox_event (
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

    private PayrollPeriodResponse mapPayrollPeriod(ResultSet rs) throws java.sql.SQLException {
        return new PayrollPeriodResponse(
                rs.getLong("id"),
                rs.getLong("region_id"),
                rs.getString("reference_code"),
                rs.getString("name"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getObject("pay_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("note")
        );
    }

    private JsonNode readTree(String value) {
        try {
            return value == null ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to parse JSON value", exception);
        }
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
            } else if (value instanceof JsonNode jsonNode) {
                parameters.addValue((String) values[index], jsonNode.toString());
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private String currentCorrelationId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest().getHeader(CorrelationId.HEADER);
    }

    private record PayrollPeriodRecord(Long id, Long regionId, LocalDate startDate, LocalDate endDate, LocalDate payDate, String status) {
    }

    private record PayrollRunRecord(Long id, Long payrollPeriodId, LocalDate runDate, String status, BigDecimal totalAmount) {
    }

    private record DocumentNumberAllocation(String prefix, long allocatedNumber) {
    }

    private record EffectiveContract(
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

    private record ApprovedAttendance(
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

    private record PayrollLine(String lineType, String description, BigDecimal amount) {
    }

    private record OutletAllocation(Long outletId, BigDecimal workHours, BigDecimal allocatedAmount) {
    }

    private record PayrollEmployeeComputation(
            Long primaryContractId,
            Long primaryOutletId,
            BigDecimal grossPay,
            BigDecimal deductionAmount,
            BigDecimal taxAmount,
            BigDecimal netPay,
            BigDecimal workDays,
            BigDecimal workHours,
            BigDecimal overtimeHours,
            String exceptionMessage,
            List<PayrollLine> lines,
            List<OutletAllocation> allocations
    ) {
    }
}
