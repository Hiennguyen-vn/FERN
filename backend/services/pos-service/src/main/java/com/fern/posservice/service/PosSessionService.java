package com.fern.posservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.posservice.dto.PosCommands.OpenSessionRequest;
import com.fern.posservice.dto.PosCommands.ReconcileSessionRequest;
import com.fern.posservice.dto.PosResponses.PosSessionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PosSessionService {
    private static final String PAYMENT_METHOD_CASH = "CASH";

    private final PosAuthorizer posAuthorizer;
    private final PosStore store;
    private final PosOrgClient posOrgClient;
    private final PosReferenceCodeGenerator codeGenerator;
    private final Clock clock;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;

    public PosSessionService(
            PosAuthorizer posAuthorizer,
            PosStore store,
            PosOrgClient posOrgClient,
            PosReferenceCodeGenerator codeGenerator,
            Clock clock,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver
    ) {
        this.posAuthorizer = posAuthorizer;
        this.store = store;
        this.posOrgClient = posOrgClient;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
    }

    public PosSessionOpenResult openSession(FernPrincipal principal, OpenSessionRequest request) {
        posAuthorizer.requireRoutePermission(principal, request.regionId(), request.outletId(), PermissionCodes.POS_SESSION_OPEN);
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new ConflictException("Outlet route does not match requested region");
        }
        PosOrgClient.RegionRoute region = posOrgClient.requireRegion(outlet.regionId());
        if (!region.currencyCode().equalsIgnoreCase(request.currencyCode())) {
            throw new ConflictException("Session currency does not match outlet region currency");
        }
        ensureOutletOperational(outlet, request.businessDate());
        TransactionTemplate transactionTemplate = transactionTemplate(outlet.regionId(), request.outletId());
        return transactionTemplate.execute(status -> openSessionTx(principal, request, outlet, region));
    }

    private PosSessionOpenResult openSessionTx(
            FernPrincipal principal,
            OpenSessionRequest request,
            PosOrgClient.OutletRoute outlet,
            PosOrgClient.RegionRoute region
    ) {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), request.outletId());
        lockOpenSessionScope(jdbcTemplate, request.outletId());
        SessionRecord existingOpenSession = findOpenSession(jdbcTemplate, request.outletId(), request.terminalId());
        if (existingOpenSession != null) {
            if (!java.util.Objects.equals(existingOpenSession.cashierUserId(), principal.userId())) {
                throw new ConflictException("Another cashier already has an open session for this outlet and terminal");
            }
            return new PosSessionOpenResult(getSession(principal, existingOpenSession.id()), true);
        }
        Long id = PosSql.insertForId(jdbcTemplate, """
                INSERT INTO pos.pos_session (
                    session_code, region_id, outlet_id, currency_code, cashier_user_id, manager_user_id,
                    terminal_id, opened_at, business_date, status, note, created_at, updated_at
                ) VALUES (
                    :sessionCode, :regionId, :outletId, :currencyCode, :cashierUserId, NULL,
                    :terminalId, :openedAt, :businessDate, :status, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PosSql.params(
                "sessionCode", codeGenerator.nextSessionCode(),
                "regionId", outlet.regionId(),
                "outletId", request.outletId(),
                "currencyCode", region.currencyCode(),
                "cashierUserId", principal.userId(),
                "terminalId", request.terminalId(),
                "openedAt", clock.instant(),
                "businessDate", request.businessDate(),
                "status", PosSessionStatus.OPEN.name(),
                "note", request.note()
        ));
        return new PosSessionOpenResult(getSession(principal, id), false);
    }

    @Transactional(readOnly = true)
    public PosSessionResponse getSession(FernPrincipal principal, Long id) {
        SessionRecord session = store.requireSession(id);
        posAuthorizer.requireRoutePermission(principal, session.regionId(), session.outletId(), PermissionCodes.POS_SESSION_READ);
        return store.mapSession(session);
    }

    @Transactional(readOnly = true)
    public List<PosSessionResponse> listSessions(
            FernPrincipal principal,
            Long outletId,
            String terminalId,
            String status,
            LocalDate businessDate,
            int limit
    ) {
        posAuthorizer.requireOutletPermission(principal, outletId, PermissionCodes.POS_SESSION_READ);
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(outletId);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(outlet.regionId(), outletId);
        StringBuilder sql = new StringBuilder("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE outlet_id = :outletId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("outletId", outletId);
        if (terminalId != null && !terminalId.isBlank()) {
            sql.append(" AND terminal_id = :terminalId");
            params.addValue("terminalId", terminalId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            params.addValue("status", status);
        }
        if (businessDate != null) {
            sql.append(" AND business_date = :businessDate");
            params.addValue("businessDate", businessDate);
        }
        sql.append("""
                
                ORDER BY opened_at DESC
                LIMIT :limit
                """);
        params.addValue("limit", limit);
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> store.mapSession(new SessionRecord(
                rs.getLong("id"),
                rs.getString("session_code"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("terminal_id"),
                rs.getString("currency_code"),
                rs.getObject("cashier_user_id", Long.class),
                rs.getObject("manager_user_id", Long.class),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("note"),
                PosSql.instant(rs, "opened_at"),
                PosSql.instant(rs, "closed_at"),
                PosSql.instant(rs, "reconciled_at"),
                rs.getBigDecimal("expected_cash_amount"),
                rs.getBigDecimal("counted_cash_amount"),
                rs.getBigDecimal("discrepancy_amount")
        )));
    }

    @Transactional
    public PosSessionResponse closeSession(FernPrincipal principal, Long id) {
        SessionRecord session = store.requireSessionForUpdate(id);
        posAuthorizer.requireRoutePermission(principal, session.regionId(), session.outletId(), PermissionCodes.POS_SESSION_CLOSE);
        ensureSessionStatus(session, PosSessionStatus.OPEN, "Only open sessions can be closed");
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(session.regionId(), session.outletId());
        boolean openOrders = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pos.sale_order
                    WHERE pos_session_id = :sessionId AND status IN (:openStatus, :completingStatus)
                )
                """, PosSql.params(
                "sessionId", id,
                "openStatus", SaleOrderStatus.OPEN.name(),
                "completingStatus", SaleOrderStatus.COMPLETING.name()
        ), Boolean.class));
        if (openOrders) {
            throw new ConflictException("Cannot close a POS session while open orders still exist");
        }
        // P1: Calculate expectedCashAmount at close time so UI can show it immediately
        BigDecimal expectedCash = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(payment.amount), 0)
                FROM pos.sale_payment payment
                JOIN pos.sale_order sale_order ON sale_order.id = payment.sale_order_id
                WHERE payment.pos_session_id = :sessionId
                  AND payment.status = :paymentStatus
                  AND payment.payment_method = :paymentMethod
                  AND sale_order.status = :orderStatus
                """, PosSql.params(
                "sessionId", id,
                "paymentStatus", SalePaymentStatus.SUCCESS.name(),
                "paymentMethod", PAYMENT_METHOD_CASH,
                "orderStatus", SaleOrderStatus.COMPLETED.name()
        ), BigDecimal.class);
        int updated = jdbcTemplate.update("""
                UPDATE pos.pos_session
                SET status = :status, closed_at = :closedAt, manager_user_id = :managerUserId,
                    expected_cash_amount = :expectedCashAmount,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :currentStatus
                """, PosSql.params(
                "status", PosSessionStatus.CLOSED.name(),
                "closedAt", clock.instant(),
                "managerUserId", principal.userId(),
                "expectedCashAmount", expectedCash,
                "id", id,
                "currentStatus", PosSessionStatus.OPEN.name()
        ));
        if (updated != 1) {
            throw new ConflictException("Only open sessions can be closed");
        }
        return getSession(principal, id);
    }

    @Transactional
    public PosSessionResponse reconcileSession(FernPrincipal principal, Long id, ReconcileSessionRequest request) {
        SessionRecord session = store.requireSessionForUpdate(id);
        posAuthorizer.requireRoutePermission(principal, session.regionId(), session.outletId(), PermissionCodes.POS_SESSION_RECONCILE);
        ensureSessionStatus(session, PosSessionStatus.CLOSED, "Only closed sessions can be reconciled");
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(session.regionId(), session.outletId());
        BigDecimal expectedCash = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(payment.amount), 0)
                FROM pos.sale_payment payment
                JOIN pos.sale_order sale_order ON sale_order.id = payment.sale_order_id
                WHERE payment.pos_session_id = :sessionId
                  AND payment.status = :paymentStatus
                  AND payment.payment_method = :paymentMethod
                  AND sale_order.status = :orderStatus
                """, PosSql.params(
                "sessionId", id,
                "paymentStatus", SalePaymentStatus.SUCCESS.name(),
                "paymentMethod", PAYMENT_METHOD_CASH,
                "orderStatus", SaleOrderStatus.COMPLETED.name()
        ), BigDecimal.class);
        BigDecimal discrepancy = request.countedCashAmount().subtract(expectedCash);
        int updated = jdbcTemplate.update("""
                UPDATE pos.pos_session
                SET status = :status,
                    manager_user_id = :managerUserId,
                    reconciled_at = :reconciledAt,
                    expected_cash_amount = :expectedCashAmount,
                    counted_cash_amount = :countedCashAmount,
                    discrepancy_amount = :discrepancyAmount,
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :currentStatus
                """, PosSql.params(
                "status", PosSessionStatus.RECONCILED.name(),
                "managerUserId", principal.userId(),
                "reconciledAt", clock.instant(),
                "expectedCashAmount", expectedCash,
                "countedCashAmount", request.countedCashAmount(),
                "discrepancyAmount", discrepancy,
                "note", request.note(),
                "id", id,
                "currentStatus", PosSessionStatus.CLOSED.name()
        ));
        if (updated != 1) {
            throw new ConflictException("Only closed sessions can be reconciled");
        }
        return getSession(principal, id);
    }

    private void ensureSessionStatus(SessionRecord session, PosSessionStatus expected, String message) {
        if (!expected.name().equals(session.status())) {
            throw new ConflictException(message);
        }
    }

    private void ensureOutletOperational(PosOrgClient.OutletRoute outlet, LocalDate businessDate) {
        if (!outlet.isActive() || outlet.isClosedOn(businessDate)) {
            throw new ConflictException("Outlet is inactive or closed for POS transactions");
        }
    }

    private SessionRecord findOpenSession(NamedParameterJdbcTemplate jdbcTemplate, Long outletId, String terminalId) {
        return jdbcTemplate.query("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE outlet_id = :outletId
                  AND status = :status
                  AND (
                        (CAST(:terminalId AS VARCHAR) IS NULL AND terminal_id IS NULL)
                     OR terminal_id = CAST(:terminalId AS VARCHAR)
                  )
                ORDER BY opened_at DESC, id DESC
                LIMIT 1
                """, PosSql.params(
                "outletId", outletId,
                "terminalId", terminalId,
                "status", PosSessionStatus.OPEN.name()
        ), rs -> rs.next() ? new SessionRecord(
                rs.getLong("id"),
                rs.getString("session_code"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("terminal_id"),
                rs.getString("currency_code"),
                rs.getObject("cashier_user_id", Long.class),
                rs.getObject("manager_user_id", Long.class),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("note"),
                PosSql.instant(rs, "opened_at"),
                PosSql.instant(rs, "closed_at"),
                PosSql.instant(rs, "reconciled_at"),
                rs.getBigDecimal("expected_cash_amount"),
                rs.getBigDecimal("counted_cash_amount"),
                rs.getBigDecimal("discrepancy_amount")
        ) : null);
    }

    private void lockOpenSessionScope(NamedParameterJdbcTemplate jdbcTemplate, Long outletId) {
        jdbcTemplate.query("""
                SELECT pg_advisory_xact_lock(:lockKey)
                """, PosSql.params("lockKey", outletId), rs -> null);
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
    }

    private TransactionTemplate transactionTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).tx();
    }
}
