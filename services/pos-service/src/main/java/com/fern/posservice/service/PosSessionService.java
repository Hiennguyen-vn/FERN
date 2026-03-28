package com.fern.posservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.posservice.dto.PosCommands.OpenSessionRequest;
import com.fern.posservice.dto.PosCommands.ReconcileSessionRequest;
import com.fern.posservice.dto.PosResponses.PosSessionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PosSessionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PosAuthorizer posAuthorizer;
    private final PosStore store;
    private final PosOrgClient posOrgClient;
    private final PosReferenceCodeGenerator codeGenerator;
    private final Clock clock;

    public PosSessionService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PosAuthorizer posAuthorizer,
            PosStore store,
            PosOrgClient posOrgClient,
            PosReferenceCodeGenerator codeGenerator,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.posAuthorizer = posAuthorizer;
        this.store = store;
        this.posOrgClient = posOrgClient;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Transactional
    public PosSessionResponse openSession(FernPrincipal principal, OpenSessionRequest request) {
        posAuthorizer.requireRoutePermission(principal, request.regionId(), request.outletId(), PermissionCodes.POS_SESSION_OPEN);
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new ConflictException("Outlet route does not match requested region");
        }
        lockOpenSessionScope(request.outletId());
        boolean openExists;
        if (request.terminalId() != null) {
            openExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pos.pos_session
                        WHERE outlet_id = :outletId
                          AND terminal_id = :terminalId
                          AND status = :status
                    )
                    """, PosSql.params(
                    "outletId", request.outletId(),
                    "terminalId", request.terminalId(),
                    "status", PosSessionStatus.OPEN.name()
            ), Boolean.class));
        } else {
            openExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pos.pos_session
                        WHERE outlet_id = :outletId
                          AND status = :status
                          AND terminal_id IS NULL
                    )
                    """, PosSql.params(
                    "outletId", request.outletId(),
                    "status", PosSessionStatus.OPEN.name()
            ), Boolean.class));
        }
        if (openExists) {
            throw new ConflictException("The outlet already has an open POS session");
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
                "currencyCode", request.currencyCode(),
                "cashierUserId", principal.userId(),
                "terminalId", request.terminalId(),
                "openedAt", clock.instant(),
                "businessDate", request.businessDate(),
                "status", PosSessionStatus.OPEN.name(),
                "note", request.note()
        ));
        return getSession(principal, id);
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
            LocalDate businessDate
    ) {
        posAuthorizer.requireOutletPermission(principal, outletId, PermissionCodes.POS_SESSION_READ);
        return jdbcTemplate.query("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE outlet_id = :outletId
                  AND (CAST(:terminalId AS VARCHAR) IS NULL OR terminal_id = CAST(:terminalId AS VARCHAR))
                  AND (:status IS NULL OR status = :status)
                  AND (:businessDate IS NULL OR business_date = :businessDate)
                ORDER BY opened_at DESC
                """, PosSql.params(
                "outletId", outletId,
                "terminalId", terminalId,
                "status", status,
                "businessDate", businessDate
        ), (rs, rowNum) -> store.mapSession(new SessionRecord(
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
        boolean openOrders = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pos.sale_order
                    WHERE pos_session_id = :sessionId AND status = :status
                )
                """, PosSql.params("sessionId", id, "status", SaleOrderStatus.OPEN.name()), Boolean.class));
        if (openOrders) {
            throw new ConflictException("Cannot close a POS session while open orders still exist");
        }
        int updated = jdbcTemplate.update("""
                UPDATE pos.pos_session
                SET status = :status, closed_at = :closedAt, manager_user_id = :managerUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :currentStatus
                """, PosSql.params(
                "status", PosSessionStatus.CLOSED.name(),
                "closedAt", clock.instant(),
                "managerUserId", principal.userId(),
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
        BigDecimal expectedCash = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(payment.amount), 0)
                FROM pos.sale_payment payment
                JOIN pos.sale_order sale_order ON sale_order.id = payment.sale_order_id
                WHERE payment.pos_session_id = :sessionId
                  AND payment.status = :paymentStatus
                  AND payment.payment_method = 'CASH'
                  AND sale_order.status = :orderStatus
                """, PosSql.params(
                "sessionId", id,
                "paymentStatus", SalePaymentStatus.SUCCESS.name(),
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

    private void lockOpenSessionScope(Long outletId) {
        jdbcTemplate.query("""
                SELECT pg_advisory_xact_lock(:lockKey)
                """, PosSql.params("lockKey", outletId), rs -> null);
    }
}
