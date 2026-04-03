package com.fern.posservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.posservice.dto.PosResponses.OutletTodayStatResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PosStatsService {
    private final PosAuthorizer posAuthorizer;
    private final PosOrgClient posOrgClient;
    private final Clock clock;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;

    public PosStatsService(
            PosAuthorizer posAuthorizer,
            PosOrgClient posOrgClient,
            Clock clock,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver
    ) {
        this.posAuthorizer = posAuthorizer;
        this.posOrgClient = posOrgClient;
        this.clock = clock;
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
    }

    public List<OutletTodayStatResponse> listTodayStats(FernPrincipal principal, List<Long> outletIds) {
        List<Long> normalizedOutletIds = normalizeOutletIds(outletIds);
        if (normalizedOutletIds.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, OutletBusinessContext> contexts = resolveOutletContexts(principal, normalizedOutletIds);
        Map<Long, SessionRecord> sessionByOutlet = findLatestSessionsByOutlet(contexts);
        Map<Long, SessionStats> statsBySessionId = findStatsBySessionId(sessionByOutlet.values().stream().map(SessionRecord::id).toList());

        List<OutletTodayStatResponse> rows = new ArrayList<>(normalizedOutletIds.size());
        for (Long outletId : normalizedOutletIds) {
            OutletBusinessContext context = contexts.get(outletId);
            SessionRecord session = sessionByOutlet.get(outletId);
            if (session == null) {
                rows.add(zeroRow(outletId, context.currencyCode()));
                continue;
            }

            SessionStats stats = statsBySessionId.getOrDefault(session.id(), SessionStats.empty());
            rows.add(new OutletTodayStatResponse(
                    outletId,
                    session.id(),
                    session.status(),
                    session.currencyCode() == null || session.currencyCode().isBlank() ? context.currencyCode() : session.currencyCode(),
                    stats.totalOrders(),
                    stats.completed(),
                    stats.open(),
                    stats.cancelled(),
                    stats.totalRevenue(),
                    stats.cashCollected(),
                    stats.nonCashCollected()
            ));
        }
        return rows;
    }

    private List<Long> normalizeOutletIds(List<Long> outletIds) {
        if (outletIds == null || outletIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long outletId : outletIds) {
            if (outletId == null || outletId <= 0) {
                throw new BadRequestException("outletIds must contain only positive values");
            }
            unique.add(outletId);
        }
        return List.copyOf(unique);
    }

    private LinkedHashMap<Long, OutletBusinessContext> resolveOutletContexts(FernPrincipal principal, List<Long> outletIds) {
        LinkedHashMap<Long, OutletBusinessContext> contexts = new LinkedHashMap<>();
        for (Long outletId : outletIds) {
            posAuthorizer.requireOutletPermission(principal, outletId, PermissionCodes.POS_SESSION_READ);
            PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(outletId);
            PosOrgClient.RegionRoute region = posOrgClient.requireRegion(outlet.regionId());
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(region.timezoneName())));
            contexts.put(outletId, new OutletBusinessContext(outlet.regionId(), region.currencyCode(), today));
        }
        return contexts;
    }

    private Map<Long, SessionRecord> findLatestSessionsByOutlet(Map<Long, OutletBusinessContext> contexts) {
        List<Long> outletIds = new ArrayList<>(contexts.keySet());
        List<LocalDate> businessDates = contexts.values().stream()
                .map(OutletBusinessContext::businessDate)
                .distinct()
                .toList();
        if (outletIds.isEmpty() || businessDates.isEmpty()) {
            return Map.of();
        }

        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(contexts.values().iterator().next().regionId(), outletIds.get(0));
        List<SessionRecord> sessions = jdbcTemplate.query("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE outlet_id IN (:outletIds)
                  AND business_date IN (:businessDates)
                ORDER BY outlet_id ASC, opened_at DESC, id DESC
                """, PosSql.params(
                "outletIds", outletIds,
                "businessDates", businessDates
        ), (rs, rowNum) -> new SessionRecord(
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
        ));

        Map<Long, SessionRecord> latestByOutlet = new LinkedHashMap<>();
        for (SessionRecord session : sessions) {
            OutletBusinessContext context = contexts.get(session.outletId());
            if (context == null || !context.businessDate().equals(session.businessDate()) || latestByOutlet.containsKey(session.outletId())) {
                continue;
            }
            latestByOutlet.put(session.outletId(), session);
        }
        return latestByOutlet;
    }

    private Map<Long, SessionStats> findStatsBySessionId(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        // Sessions passed here were already resolved from a single shard (findLatestSessionsByOutlet uses the
        // first outlet's shard). Use the root/default shard template for this aggregation query.
        NamedParameterJdbcTemplate statsJdbc = operationalShardRegistry.get(shardResolver.resolve(com.fern.platform.common.RouteKey.of(0L, 0L))).jdbc();
        return statsJdbc.query("""
                WITH order_summary AS (
                    SELECT sale_order.pos_session_id,
                           COUNT(*) AS total_orders,
                           COUNT(*) FILTER (WHERE sale_order.status = 'COMPLETED') AS completed,
                           COUNT(*) FILTER (WHERE sale_order.status = 'OPEN') AS open,
                           COUNT(*) FILTER (WHERE sale_order.status = 'CANCELLED') AS cancelled,
                           COALESCE(SUM(CASE WHEN sale_order.status = 'COMPLETED' THEN sale_order.total_amount ELSE 0 END), 0) AS total_revenue
                    FROM pos.sale_order sale_order
                    WHERE sale_order.pos_session_id IN (:sessionIds)
                    GROUP BY sale_order.pos_session_id
                ),
                payment_summary AS (
                    SELECT sale_order.pos_session_id,
                           COALESCE(SUM(CASE
                               WHEN sale_order.status = 'COMPLETED'
                                AND sale_payment.status = 'SUCCESS'
                                AND sale_payment.payment_method = 'CASH'
                               THEN sale_payment.amount
                               ELSE 0
                           END), 0) AS cash_collected,
                           COALESCE(SUM(CASE
                               WHEN sale_order.status = 'COMPLETED'
                                AND sale_payment.status = 'SUCCESS'
                                AND sale_payment.payment_method <> 'CASH'
                               THEN sale_payment.amount
                               ELSE 0
                           END), 0) AS non_cash_collected
                    FROM pos.sale_order sale_order
                    LEFT JOIN pos.sale_payment sale_payment ON sale_payment.sale_order_id = sale_order.id
                    WHERE sale_order.pos_session_id IN (:sessionIds)
                    GROUP BY sale_order.pos_session_id
                )
                SELECT order_summary.pos_session_id,
                       order_summary.total_orders,
                       order_summary.completed,
                       order_summary.open,
                       order_summary.cancelled,
                       order_summary.total_revenue,
                       COALESCE(payment_summary.cash_collected, 0) AS cash_collected,
                       COALESCE(payment_summary.non_cash_collected, 0) AS non_cash_collected
                FROM order_summary
                LEFT JOIN payment_summary ON payment_summary.pos_session_id = order_summary.pos_session_id
                """, PosSql.params("sessionIds", sessionIds), rs -> {
            Map<Long, SessionStats> stats = new LinkedHashMap<>();
            while (rs.next()) {
                stats.put(rs.getLong("pos_session_id"), new SessionStats(
                        rs.getLong("total_orders"),
                        rs.getLong("completed"),
                        rs.getLong("open"),
                        rs.getLong("cancelled"),
                        rs.getBigDecimal("total_revenue"),
                        rs.getBigDecimal("cash_collected"),
                        rs.getBigDecimal("non_cash_collected")
                ));
            }
            return stats;
        });
    }

    private OutletTodayStatResponse zeroRow(Long outletId, String currencyCode) {
        return new OutletTodayStatResponse(
                outletId,
                null,
                "NO_SESSION",
                currencyCode,
                0L,
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
    }

    private record OutletBusinessContext(Long regionId, String currencyCode, LocalDate businessDate) {
    }

    private record SessionStats(
            Long totalOrders,
            Long completed,
            Long open,
            Long cancelled,
            BigDecimal totalRevenue,
            BigDecimal cashCollected,
            BigDecimal nonCashCollected
    ) {
        private static SessionStats empty() {
            return new SessionStats(0L, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
