package com.fern.financeservice.service;

import static com.fern.financeservice.service.FinanceJdbcSupport.insertForId;
import static com.fern.financeservice.service.FinanceJdbcSupport.instant;
import static com.fern.financeservice.service.FinanceJdbcSupport.nullableLong;
import static com.fern.financeservice.service.FinanceJdbcSupport.params;
import static com.fern.financeservice.service.FinancePrincipalSupport.actorId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fern.financeservice.dto.FinanceCommands.CreatePayrollRunRequest;
import com.fern.financeservice.dto.FinanceCommands.MarkPaidRequest;
import com.fern.financeservice.dto.FinanceResponses.PayrollAllocationResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollEmployeeResultResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollLineResponse;
import com.fern.financeservice.dto.FinanceResponses.PayrollRunResponse;
import com.fern.financeservice.service.payroll.model.ApprovedAttendance;
import com.fern.financeservice.service.payroll.model.EffectiveContract;
import com.fern.financeservice.service.payroll.model.OutletAllocation;
import com.fern.financeservice.service.payroll.model.PayrollEmployeeComputation;
import com.fern.financeservice.service.payroll.model.PayrollLine;
import com.fern.financeservice.service.payroll.model.PayrollPeriodRecord;
import com.fern.financeservice.service.payroll.model.PayrollRunRecord;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.PayrollPostedEvent.PayrollExpenseLink;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PayrollRunOrchestrator {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PayrollPeriodService payrollPeriodService;
    private final FinanceAuthorizer financeAuthorizer;
    private final FinanceAuditService financeAuditService;
    private final FinanceConfigService financeConfigService;
    private final PayrollHrClient payrollHrClient;
    private final PayrollCalculationEngine payrollCalculationEngine;
    private final FinanceOutboxService financeOutboxService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PayrollRunOrchestrator(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            PayrollPeriodService payrollPeriodService,
            FinanceAuthorizer financeAuthorizer,
            FinanceAuditService financeAuditService,
            FinanceConfigService financeConfigService,
            PayrollHrClient payrollHrClient,
            PayrollCalculationEngine payrollCalculationEngine,
            FinanceOutboxService financeOutboxService,
            Clock clock,
            TransactionTemplate transactionTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.payrollPeriodService = payrollPeriodService;
        this.financeAuthorizer = financeAuthorizer;
        this.financeAuditService = financeAuditService;
        this.financeConfigService = financeConfigService;
        this.payrollHrClient = payrollHrClient;
        this.payrollCalculationEngine = payrollCalculationEngine;
        this.financeOutboxService = financeOutboxService;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public PayrollRunResponse createPayrollRun(FernPrincipal principal, CreatePayrollRunRequest request) {
        return createPayrollRun(principal, request, null);
    }

    public PayrollRunResponse createPayrollRun(FernPrincipal principal, CreatePayrollRunRequest request, String correlationId) {
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(request.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_PREPARE);
        PrefetchedRecalculation prefetched = prefetchRecalculation(period, correlationId, actorId(principal));
        Long runId = transactionTemplate.execute(status -> {
            Long createdRunId = insertForId(jdbcTemplate, """
                    INSERT INTO finance.payroll_run (
                        payroll_period_id, run_code, run_date, status, total_amount, processed_by_user_id, note, created_at, updated_at
                    ) VALUES (
                        :payrollPeriodId, :runCode, :runDate, 'DRAFT', 0, :processedByUserId, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "payrollPeriodId", request.payrollPeriodId(),
                    "runCode", financeConfigService.nextDocumentNumber("PAYROLL_RUN"),
                    "runDate", request.runDate() == null ? LocalDate.now(clock) : request.runDate(),
                    "processedByUserId", actorId(principal),
                    "note", request.note()
            ));
            recalculateRun(createdRunId, prefetched);
            return createdRunId;
        });
        PayrollRunResponse response = getPayrollRun(principal, java.util.Objects.requireNonNull(runId));
        financeAuditService.publish("finance.payroll.draft.created", principal, correlationId, period.regionId(), null, "CREATE", "PAYROLL_RUN", runId.toString(), null, response, Map.of());
        return response;
    }

    public PayrollRunResponse submitPayrollRun(FernPrincipal principal, Long runId, String note) {
        return submitPayrollRun(principal, runId, note, null);
    }

    public PayrollRunResponse submitPayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(run.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_PREPARE);
        if (!"DRAFT".equals(run.status()) && !"REJECTED".equals(run.status())) {
            throw new BadRequestException("Only draft or rejected payroll runs can be submitted");
        }
        PrefetchedRecalculation prefetched = prefetchRecalculation(period, correlationId, actorId(principal));
        transactionTemplate.executeWithoutResult(status -> {
            PayrollRunRecord currentRun = requirePayrollRunRecord(runId);
            if (!"DRAFT".equals(currentRun.status()) && !"REJECTED".equals(currentRun.status())) {
                throw new BadRequestException("Only draft or rejected payroll runs can be submitted");
            }
            recalculateRun(runId, prefetched);
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
                    "submittedByUserId", actorId(principal),
                    "note", note,
                    "id", runId
            ));
            if (updated != 1) {
                throw new BadRequestException("Only draft or rejected payroll runs can be submitted");
            }
        });
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeAuditService.publish("finance.payroll.submitted", principal, correlationId, period.regionId(), null, "SUBMIT", "PAYROLL_RUN", runId.toString(), run, response, Map.of());
        return response;
    }

    @Transactional
    public PayrollRunResponse approvePayrollRun(FernPrincipal principal, Long runId, String note) {
        return approvePayrollRun(principal, runId, note, null);
    }

    @Transactional
    public PayrollRunResponse approvePayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(run.payrollPeriodId());
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
                "approvedByUserId", actorId(principal),
                "note", note,
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only submitted payroll runs can be approved");
        }
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeOutboxService.emitPayrollCalculated(period, response, principal, correlationId);
        financeAuditService.publish("finance.payroll.approved", principal, correlationId, period.regionId(), null, "APPROVE", "PAYROLL_RUN", runId.toString(), run, response, Map.of());
        return response;
    }

    @Transactional
    public PayrollRunResponse rejectPayrollRun(FernPrincipal principal, Long runId, String note) {
        return rejectPayrollRun(principal, runId, note, null);
    }

    @Transactional
    public PayrollRunResponse rejectPayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(run.payrollPeriodId());
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
                "rejectedByUserId", actorId(principal),
                "rejectionReason", note,
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only submitted payroll runs can be rejected");
        }
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeAuditService.publish("finance.payroll.rejected", principal, correlationId, period.regionId(), null, "REJECT", "PAYROLL_RUN", runId.toString(), run, response, Map.of("reason", note));
        return response;
    }

    public PayrollRunResponse markPayrollPaid(FernPrincipal principal, Long runId, MarkPaidRequest request) {
        return markPayrollPaid(principal, runId, request, null);
    }

    /**
     * Uses TransactionTemplate instead of @Transactional for consistency with
     * createPayrollRun() and to avoid AOP proxy bypass risks from self-invocation.
     */
    public PayrollRunResponse markPayrollPaid(FernPrincipal principal, Long runId, MarkPaidRequest request, String correlationId) {
        return transactionTemplate.execute(status -> markPayrollPaidTx(principal, runId, request, correlationId));
    }

    private PayrollRunResponse markPayrollPaidTx(FernPrincipal principal, Long runId, MarkPaidRequest request, String correlationId) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(run.payrollPeriodId());
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
                "paidByUserId", actorId(principal),
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
                        "referenceCode", financeConfigService.nextDocumentNumber("PAYROLL_EXPENSE"),
                        "expenseTime", clock.instant(),
                        "amount", allocation.allocatedAmount(),
                        "note", "Payroll run " + runId + " paid",
                        "submittedByUserId", actorId(principal),
                        "approvedByUserId", actorId(principal)
                ));
                jdbcTemplate.update("""
                        INSERT INTO finance.expense_payroll (expense_record_id, payroll_run_id)
                        VALUES (:expenseRecordId, :payrollRunId)
                        ON CONFLICT (expense_record_id) DO NOTHING
                        """, params("expenseRecordId", expenseRecordId, "payrollRunId", runId));
                financeOutboxService.emitExpensePosted(
                        expenseRecordId,
                        period.regionId(),
                        allocation.outletId(),
                        employee.employeeId(),
                        runId,
                        run.runDate(),
                        "PAYROLL",
                        allocation.allocatedAmount(),
                        correlationId,
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
        financeOutboxService.emitPayrollPosted(period, response, principal, request.paymentReference(), links, correlationId);
        financeAuditService.publish("finance.payroll.paid", principal, correlationId, period.regionId(), null, "MARK_PAID", "PAYROLL_RUN", runId.toString(), run, response, Map.of("paymentReference", request.paymentReference()));
        return response;
    }

    @Transactional
    public PayrollRunResponse cancelPayrollRun(FernPrincipal principal, Long runId, String note) {
        return cancelPayrollRun(principal, runId, note, null);
    }

    @Transactional
    public PayrollRunResponse cancelPayrollRun(FernPrincipal principal, Long runId, String note, String correlationId) {
        PayrollRunRecord run = requirePayrollRunRecord(runId);
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(run.payrollPeriodId());
        financeAuthorizer.requireRegionPermission(principal, period.regionId(), PermissionCodes.FINANCE_PAYROLL_PREPARE);
        if (!"DRAFT".equals(run.status()) && !"REJECTED".equals(run.status())) {
            throw new BadRequestException("Only draft or rejected payroll runs can be cancelled");
        }
        int updated = jdbcTemplate.update("""
                UPDATE finance.payroll_run
                SET status = 'CANCELLED',
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status IN ('DRAFT', 'REJECTED')
                """, params(
                "note", note,
                "id", runId
        ));
        if (updated != 1) {
            throw new BadRequestException("Only draft or rejected payroll runs can be cancelled");
        }
        PayrollRunResponse response = getPayrollRun(principal, runId);
        financeAuditService.publish("finance.payroll.cancelled", principal, correlationId, period.regionId(), null, "CANCEL", "PAYROLL_RUN", runId.toString(), run, response, Map.of("reason", note));
        return response;
    }

    public List<PayrollRunResponse> listPayrollRuns(FernPrincipal principal, Long regionId) {
        return listPayrollRuns(principal, regionId, ListQueryDefaults.DEFAULT_LIMIT);
    }

    public List<PayrollRunResponse> listPayrollRuns(FernPrincipal principal, Long regionId, int limit) {
        List<Long> runIds;
        if (regionId != null) {
            financeAuthorizer.requireRegionPermission(principal, regionId, PermissionCodes.FINANCE_PAYROLL_READ);
            runIds = jdbcTemplate.queryForList("""
                    SELECT pr.id
                    FROM finance.payroll_run pr
                    JOIN finance.payroll_period pp ON pp.id = pr.payroll_period_id
                    WHERE pp.region_id = :regionId
                    ORDER BY pr.run_date DESC, pr.id DESC
                    LIMIT :limit
                    """, params("regionId", regionId, "limit", limit), Long.class);
        } else {
            financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_PAYROLL_READ);
            runIds = jdbcTemplate.queryForList("""
                    SELECT pr.id
                    FROM finance.payroll_run pr
                    JOIN finance.payroll_period pp ON pp.id = pr.payroll_period_id
                    ORDER BY pr.run_date DESC, pr.id DESC
                    LIMIT :limit
                    """, params("limit", limit), Long.class);
        }
        if (runIds.isEmpty()) {
            return List.of();
        }
        return batchAssemblePayrollRuns(runIds);
    }

    public PayrollRunResponse getPayrollRun(FernPrincipal principal, Long id) {
        PayrollRunRecord record = requirePayrollRunRecord(id);
        PayrollPeriodRecord period = payrollPeriodService.requirePayrollPeriodRecord(record.payrollPeriodId());
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

    public PayrollRunRecord requirePayrollRunRecord(Long id) {
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

    private PrefetchedRecalculation prefetchRecalculation(PayrollPeriodRecord period, String correlationId, Long actorUserId) {
        List<EffectiveContract> contracts = payrollHrClient.fetchEffectiveContracts(period.regionId(), period.startDate(), period.endDate(), correlationId, actorUserId);
        List<ApprovedAttendance> attendance = payrollHrClient.fetchApprovedAttendance(period.regionId(), period.startDate(), period.endDate(), correlationId, actorUserId);
        JsonNode overtimePolicy = financeConfigService.readPolicyValue("payroll.overtime");
        JsonNode allowancePolicy = financeConfigService.readPolicyValue("payroll.allowance");
        JsonNode deductionPolicy = financeConfigService.readPolicyValue("payroll.deduction");
        JsonNode taxPolicy = financeConfigService.readPolicyValue("payroll.tax");
        JsonNode roundingPolicy = financeConfigService.readPolicyValue("payroll.rounding");
        return new PrefetchedRecalculation(period, contracts, attendance, overtimePolicy, allowancePolicy, deductionPolicy, taxPolicy, roundingPolicy);
    }

    private void recalculateRun(Long runId, PrefetchedRecalculation prefetched) {
        deleteExistingRunArtifacts(runId);
        Map<Long, List<EffectiveContract>> contractsByEmployee = new HashMap<>();
        for (EffectiveContract contract : prefetched.contracts()) {
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
        for (ApprovedAttendance item : prefetched.attendance()) {
            attendanceByEmployee.computeIfAbsent(item.employeeId(), ignored -> new ArrayList<>()).add(item);
        }

        int scale = prefetched.roundingPolicy().path("scale").asInt(2);
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
            PayrollEmployeeComputation computation = payrollCalculationEngine.computeEmployeePayroll(
                    prefetched.period(),
                    employeeAttendance,
                    employeeContracts,
                    scale,
                    prefetched.overtimePolicy(),
                    prefetched.allowancePolicy(),
                    prefetched.deductionPolicy(),
                    prefetched.taxPolicy()
            );
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
                        "contractId", payrollCalculationEngine.selectContract(employeeContracts, item.businessDate()).contractId(),
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

    /**
     * Batch-fetches all payroll runs by ID, assembling employees/lines/allocations
     * with only 4 queries total instead of the previous 1 + N×3 pattern.
     */
    private List<PayrollRunResponse> batchAssemblePayrollRuns(List<Long> runIds) {
        // 1. Batch-fetch all run rows
        List<PayrollRunResponse> shells = jdbcTemplate.query("""
                SELECT id, payroll_period_id, run_code, run_date, status, total_amount, payment_ref, note,
                       submitted_at, approved_at, paid_at
                FROM finance.payroll_run
                WHERE id IN (:ids)
                ORDER BY run_date DESC, id DESC
                """, params("ids", runIds), (rs, rowNum) -> new PayrollRunResponse(
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
                List.of() // placeholder — assembled below
        ));
        if (shells.isEmpty()) {
            return List.of();
        }

        // 2. Batch-fetch all employee results for these runs
        List<EmployeeResultRow> employeeRows = jdbcTemplate.query("""
                SELECT id, payroll_run_id, employee_id, contract_id, outlet_id, gross_pay, deduction_amount, tax_amount, net_pay,
                       work_days, work_hours, overtime_hours, payment_status, exception_message
                FROM finance.payroll_employee_result
                WHERE payroll_run_id IN (:ids)
                ORDER BY payroll_run_id, employee_id, id
                """, params("ids", runIds), (rs, rowNum) -> new EmployeeResultRow(
                rs.getLong("id"),
                rs.getLong("payroll_run_id"),
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
                rs.getString("exception_message")
        ));
        List<Long> resultIds = employeeRows.stream().map(EmployeeResultRow::id).toList();

        // 3. Batch-fetch all lines for these employee results
        Map<Long, List<PayrollLineResponse>> linesByResultId = new LinkedHashMap<>();
        if (!resultIds.isEmpty()) {
            jdbcTemplate.query("""
                    SELECT id, payroll_employee_result_id, line_type, description, amount
                    FROM finance.payroll_result_line
                    WHERE payroll_employee_result_id IN (:ids)
                    ORDER BY payroll_employee_result_id, id
                    """, params("ids", resultIds), (rs, rowNum) -> {
                linesByResultId
                        .computeIfAbsent(rs.getLong("payroll_employee_result_id"), k -> new ArrayList<>())
                        .add(new PayrollLineResponse(
                                rs.getLong("id"),
                                rs.getString("line_type"),
                                rs.getString("description"),
                                rs.getBigDecimal("amount")
                        ));
                return null;
            });
        }

        // 4. Batch-fetch all allocations for these employee results
        Map<Long, List<PayrollAllocationResponse>> allocationsByResultId = new LinkedHashMap<>();
        if (!resultIds.isEmpty()) {
            jdbcTemplate.query("""
                    SELECT id, payroll_employee_result_id, outlet_id, work_hours, allocated_amount
                    FROM finance.payroll_result_allocation
                    WHERE payroll_employee_result_id IN (:ids)
                    ORDER BY payroll_employee_result_id, id
                    """, params("ids", resultIds), (rs, rowNum) -> {
                allocationsByResultId
                        .computeIfAbsent(rs.getLong("payroll_employee_result_id"), k -> new ArrayList<>())
                        .add(new PayrollAllocationResponse(
                                rs.getLong("id"),
                                rs.getLong("outlet_id"),
                                rs.getBigDecimal("work_hours"),
                                rs.getBigDecimal("allocated_amount")
                        ));
                return null;
            });
        }

        // 5. Assemble employee responses grouped by run
        Map<Long, List<PayrollEmployeeResultResponse>> employeesByRunId = new LinkedHashMap<>();
        for (EmployeeResultRow row : employeeRows) {
            employeesByRunId
                    .computeIfAbsent(row.payrollRunId(), k -> new ArrayList<>())
                    .add(new PayrollEmployeeResultResponse(
                            row.id(),
                            row.employeeId(),
                            row.contractId(),
                            row.outletId(),
                            row.grossPay(),
                            row.deductionAmount(),
                            row.taxAmount(),
                            row.netPay(),
                            row.workDays(),
                            row.workHours(),
                            row.overtimeHours(),
                            row.paymentStatus(),
                            row.exceptionMessage(),
                            linesByResultId.getOrDefault(row.id(), List.of()),
                            allocationsByResultId.getOrDefault(row.id(), List.of())
                    ));
        }

        // 6. Replace placeholder employees in shells
        return shells.stream()
                .map(shell -> new PayrollRunResponse(
                        shell.id(),
                        shell.payrollPeriodId(),
                        shell.runCode(),
                        shell.runDate(),
                        shell.status(),
                        shell.totalAmount(),
                        shell.paymentRef(),
                        shell.note(),
                        shell.submittedAt(),
                        shell.approvedAt(),
                        shell.paidAt(),
                        employeesByRunId.getOrDefault(shell.id(), List.of())
                ))
                .toList();
    }

    private List<PayrollEmployeeResultResponse> queryPayrollEmployees(Long runId) {
        List<PayrollRunResponse> assembled = batchAssemblePayrollRuns(List.of(runId));
        return assembled.isEmpty() ? List.of() : assembled.getFirst().employees();
    }

    private record EmployeeResultRow(
            Long id,
            Long payrollRunId,
            Long employeeId,
            Long contractId,
            Long outletId,
            BigDecimal grossPay,
            BigDecimal deductionAmount,
            BigDecimal taxAmount,
            BigDecimal netPay,
            BigDecimal workDays,
            BigDecimal workHours,
            BigDecimal overtimeHours,
            String paymentStatus,
            String exceptionMessage
    ) {
    }

    private record PrefetchedRecalculation(
            PayrollPeriodRecord period,
            List<EffectiveContract> contracts,
            List<ApprovedAttendance> attendance,
            JsonNode overtimePolicy,
            JsonNode allowancePolicy,
            JsonNode deductionPolicy,
            JsonNode taxPolicy,
            JsonNode roundingPolicy
    ) {
    }
}
