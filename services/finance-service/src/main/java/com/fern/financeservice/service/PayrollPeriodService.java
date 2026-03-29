package com.fern.financeservice.service;

import static com.fern.financeservice.service.FinanceJdbcSupport.insertForId;
import static com.fern.financeservice.service.FinanceJdbcSupport.params;

import com.fern.financeservice.dto.FinanceCommands.CreatePayrollPeriodRequest;
import com.fern.financeservice.dto.FinanceResponses.PayrollPeriodResponse;
import com.fern.financeservice.service.payroll.model.PayrollPeriodRecord;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollPeriodService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FinanceAuthorizer financeAuthorizer;
    private final FinanceAuditService financeAuditService;
    private final FinanceConfigService financeConfigService;

    public PayrollPeriodService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            FinanceAuthorizer financeAuthorizer,
            FinanceAuditService financeAuditService,
            FinanceConfigService financeConfigService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.financeAuthorizer = financeAuthorizer;
        this.financeAuditService = financeAuditService;
        this.financeConfigService = financeConfigService;
    }

    @Transactional
    public PayrollPeriodResponse createPayrollPeriod(FernPrincipal principal, CreatePayrollPeriodRequest request) {
        return createPayrollPeriod(principal, request, null);
    }

    @Transactional
    public PayrollPeriodResponse createPayrollPeriod(FernPrincipal principal, CreatePayrollPeriodRequest request, String correlationId) {
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
                "referenceCode", financeConfigService.nextDocumentNumber("PAYROLL_PERIOD"),
                "name", request.name(),
                "startDate", request.startDate(),
                "endDate", request.endDate(),
                "payDate", request.payDate(),
                "note", request.note()
        ));
        PayrollPeriodResponse response = getPayrollPeriod(principal, id);
        financeAuditService.publish("finance.payroll.period.created", principal, correlationId, request.regionId(), null, "CREATE", "PAYROLL_PERIOD", id.toString(), null, response, Map.of());
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

    public PayrollPeriodRecord requirePayrollPeriodRecord(Long id) {
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
}
