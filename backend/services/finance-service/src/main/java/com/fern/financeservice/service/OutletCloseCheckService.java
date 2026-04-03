package com.fern.financeservice.service;

import static com.fern.financeservice.service.FinanceJdbcSupport.params;

import com.fern.financeservice.dto.FinanceResponses.OutletCloseCheckResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OutletCloseCheckService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OutletCloseCheckService(@Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OutletCloseCheckResponse getOutletCloseCheck(Long outletId) {
        Long blockingPayrollRuns = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT pr.id)
                FROM finance.payroll_run pr
                JOIN finance.payroll_employee_result per ON per.payroll_run_id = pr.id
                JOIN finance.payroll_result_allocation pra ON pra.payroll_employee_result_id = per.id
                WHERE pra.outlet_id = :outletId
                  AND pr.status IN ('SUBMITTED', 'APPROVED')
                """, params("outletId", outletId), Long.class);
        long safeBlockingPayrollRuns = blockingPayrollRuns == null ? 0L : blockingPayrollRuns;
        return new OutletCloseCheckResponse(outletId, safeBlockingPayrollRuns, safeBlockingPayrollRuns > 0);
    }
}
