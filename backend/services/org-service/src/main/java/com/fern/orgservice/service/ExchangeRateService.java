package com.fern.orgservice.service;

import com.fern.orgservice.dto.ExchangeRateResponse;
import com.fern.orgservice.dto.UpsertExchangeRateRequest;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeRateService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OrgAuthorizer orgAuthorizer;

    public ExchangeRateService(NamedParameterJdbcTemplate jdbcTemplate, OrgAuthorizer orgAuthorizer) {
        this.jdbcTemplate = jdbcTemplate;
        this.orgAuthorizer = orgAuthorizer;
    }

    public List<ExchangeRateResponse> list(FernPrincipal principal, String fromCurrency, String toCurrency, LocalDate asOfDate) {
        orgAuthorizer.requirePermission(principal, PermissionCodes.FINANCE_CONFIG_READ);
        StringBuilder sql = new StringBuilder("""
                SELECT from_currency_code, to_currency_code, rate, effective_from, effective_to
                FROM org.exchange_rate
                WHERE 1=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (fromCurrency != null && !fromCurrency.isBlank()) {
            sql.append(" AND from_currency_code = :fromCurrency");
            params.addValue("fromCurrency", fromCurrency);
        }
        if (toCurrency != null && !toCurrency.isBlank()) {
            sql.append(" AND to_currency_code = :toCurrency");
            params.addValue("toCurrency", toCurrency);
        }
        if (asOfDate != null) {
            sql.append(" AND effective_from <= :asOfDate AND (effective_to IS NULL OR effective_to >= :asOfDate)");
            params.addValue("asOfDate", asOfDate);
        }
        sql.append(" ORDER BY from_currency_code, to_currency_code, effective_from DESC LIMIT 200");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new ExchangeRateResponse(
                rs.getString("from_currency_code"),
                rs.getString("to_currency_code"),
                rs.getBigDecimal("rate"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class)
        ));
    }

    @Transactional
    public ExchangeRateResponse upsert(FernPrincipal principal, UpsertExchangeRateRequest request) {
        orgAuthorizer.requirePermission(principal, PermissionCodes.FINANCE_CONFIG_WRITE);
        jdbcTemplate.update("""
                INSERT INTO org.exchange_rate (from_currency_code, to_currency_code, rate, effective_from, effective_to)
                VALUES (:fromCurrency, :toCurrency, :rate, :effectiveFrom, :effectiveTo)
                ON CONFLICT (from_currency_code, to_currency_code, effective_from)
                DO UPDATE SET rate = EXCLUDED.rate,
                              effective_to = EXCLUDED.effective_to,
                              updated_at = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("fromCurrency", request.fromCurrencyCode())
                .addValue("toCurrency", request.toCurrencyCode())
                .addValue("rate", request.rate())
                .addValue("effectiveFrom", request.effectiveFrom())
                .addValue("effectiveTo", request.effectiveTo()));
        return new ExchangeRateResponse(
                request.fromCurrencyCode(),
                request.toCurrencyCode(),
                request.rate(),
                request.effectiveFrom(),
                request.effectiveTo()
        );
    }

    @Transactional
    public void delete(FernPrincipal principal, String fromCurrency, String toCurrency, LocalDate effectiveFrom) {
        orgAuthorizer.requirePermission(principal, PermissionCodes.FINANCE_CONFIG_WRITE);
        jdbcTemplate.update("""
                DELETE FROM org.exchange_rate
                WHERE from_currency_code = :fromCurrency
                  AND to_currency_code = :toCurrency
                  AND effective_from = :effectiveFrom
                """, new MapSqlParameterSource()
                .addValue("fromCurrency", fromCurrency)
                .addValue("toCurrency", toCurrency)
                .addValue("effectiveFrom", effectiveFrom));
    }
}
