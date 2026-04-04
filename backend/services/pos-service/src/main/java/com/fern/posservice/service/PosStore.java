package com.fern.posservice.service;

import com.fern.platform.common.ResourceNotFoundException;
import com.fern.posservice.dto.PosResponses.PosSessionResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderLineResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import com.fern.posservice.dto.PosResponses.SalePaymentResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Shard-aware data access layer for POS entities.
 *
 * <p>Every method accepts an explicit {@link NamedParameterJdbcTemplate} that has already been
 * resolved to the correct shard by the calling service.  Callers that execute multiple operations
 * inside a single transaction MUST pass the same template instance so they all participate in the
 * same connection/transaction boundary.
 */
@Component
public class PosStore {

    // No injected jdbcTemplate — callers must supply the shard-resolved template.

    public SessionRecord requireSession(NamedParameterJdbcTemplate jdbcTemplate, Long id) {
        return requireSession(jdbcTemplate, id, false);
    }

    public SessionRecord requireSessionForUpdate(NamedParameterJdbcTemplate jdbcTemplate, Long id) {
        return requireSession(jdbcTemplate, id, true);
    }

    private SessionRecord requireSession(NamedParameterJdbcTemplate jdbcTemplate, Long id, boolean forUpdate) {
        SessionRecord record = jdbcTemplate.query("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE id = :id
                %s
                """.formatted(forUpdate ? "FOR UPDATE" : ""), PosSql.params("id", id), rs -> rs.next() ? new SessionRecord(
                rs.getLong("id"),
                rs.getString("session_code"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("terminal_id"),
                rs.getString("currency_code"),
                rs.getObject("cashier_user_id", Long.class),
                rs.getObject("manager_user_id", Long.class),
                rs.getObject("business_date", java.time.LocalDate.class),
                rs.getString("status"),
                rs.getString("note"),
                PosSql.instant(rs, "opened_at"),
                PosSql.instant(rs, "closed_at"),
                PosSql.instant(rs, "reconciled_at"),
                rs.getBigDecimal("expected_cash_amount"),
                rs.getBigDecimal("counted_cash_amount"),
                rs.getBigDecimal("discrepancy_amount")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("POS session not found");
        }
        return record;
    }

    public OrderRecord requireOrder(NamedParameterJdbcTemplate jdbcTemplate, Long id) {
        return requireOrder(jdbcTemplate, id, false);
    }

    public OrderRecord requireOrderForUpdate(NamedParameterJdbcTemplate jdbcTemplate, Long id) {
        return requireOrder(jdbcTemplate, id, true);
    }

    public OrderRecord requireOrderForCompletionPreflight(NamedParameterJdbcTemplate jdbcTemplate, Long id) {
        return requireOrder(jdbcTemplate, id, true);
    }

    private OrderRecord requireOrder(NamedParameterJdbcTemplate jdbcTemplate, Long id, boolean forUpdate) {
        OrderRecord record = jdbcTemplate.query("""
                SELECT id, order_number, region_id, outlet_id, pos_session_id, currency_code, order_type, status, payment_status,
                       subtotal, discount_amount, tax_amount, total_amount, note, created_at, completed_at, reservation_id
                FROM pos.sale_order
                WHERE id = :id
                %s
                """.formatted(forUpdate ? "FOR UPDATE" : ""), PosSql.params("id", id), rs -> rs.next() ? new OrderRecord(
                rs.getLong("id"),
                rs.getString("order_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("pos_session_id"),
                rs.getString("currency_code"),
                rs.getString("order_type"),
                rs.getString("status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                PosSql.instant(rs, "created_at"),
                PosSql.instant(rs, "completed_at"),
                rs.getObject("reservation_id", Long.class)
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Sale order not found");
        }
        return record;
    }

    public List<OrderRecord> listOrdersBySession(NamedParameterJdbcTemplate jdbcTemplate, Long posSessionId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, order_number, region_id, outlet_id, pos_session_id, currency_code, order_type, status, payment_status,
                       subtotal, discount_amount, tax_amount, total_amount, note, created_at, completed_at, reservation_id
                FROM pos.sale_order
                WHERE pos_session_id = :posSessionId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """, PosSql.params("posSessionId", posSessionId, "limit", limit), (rs, rowNum) -> new OrderRecord(
                rs.getLong("id"),
                rs.getString("order_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("pos_session_id"),
                rs.getString("currency_code"),
                rs.getString("order_type"),
                rs.getString("status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                PosSql.instant(rs, "created_at"),
                PosSql.instant(rs, "completed_at"),
                rs.getObject("reservation_id", Long.class)
        ));
    }

    public List<SaleOrderLineResponse> queryOrderLines(NamedParameterJdbcTemplate jdbcTemplate, Long saleOrderId) {
        return jdbcTemplate.query("""
                SELECT line_number, product_id, product_code, product_name_snapshot, unit_price, qty, discount_amount, tax_amount, line_total, note
                FROM pos.sale_order_line
                WHERE sale_order_id = :saleOrderId
                ORDER BY line_number
                """, PosSql.params("saleOrderId", saleOrderId), (rs, rowNum) -> new SaleOrderLineResponse(
                rs.getInt("line_number"),
                rs.getLong("product_id"),
                rs.getString("product_code"),
                rs.getString("product_name_snapshot"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("qty"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("line_total"),
                rs.getString("note")
        ));
    }

    public List<SalePaymentResponse> queryPayments(NamedParameterJdbcTemplate jdbcTemplate, Long saleOrderId) {
        return jdbcTemplate.query("""
                SELECT id, payment_method, amount, status, payment_time, transaction_ref
                FROM pos.sale_payment
                WHERE sale_order_id = :saleOrderId
                ORDER BY created_at, id
                """, PosSql.params("saleOrderId", saleOrderId), (rs, rowNum) -> new SalePaymentResponse(
                rs.getLong("id"),
                rs.getString("payment_method"),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                PosSql.instant(rs, "payment_time"),
                rs.getString("transaction_ref")
        ));
    }

    public BigDecimal successfulPaymentTotal(NamedParameterJdbcTemplate jdbcTemplate, Long saleOrderId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM pos.sale_payment
                WHERE sale_order_id = :saleOrderId AND status = :status
                """, PosSql.params("saleOrderId", saleOrderId, "status", SalePaymentStatus.SUCCESS.name()), BigDecimal.class);
    }

    public void replaceOrderLines(NamedParameterJdbcTemplate jdbcTemplate, Long saleOrderId, List<PricedLine> lines) {
        jdbcTemplate.update("DELETE FROM pos.sale_order_line WHERE sale_order_id = :saleOrderId",
                PosSql.params("saleOrderId", saleOrderId));
        if (lines.isEmpty()) {
            return;
        }
        org.springframework.jdbc.core.namedparam.SqlParameterSource[] batchParams = lines.stream()
                .map(line -> PosSql.params(
                        "saleOrderId", saleOrderId,
                        "lineNumber", line.lineNumber(),
                        "productId", line.productId(),
                        "productCode", line.productCode(),
                        "productNameSnapshot", line.productNameSnapshot(),
                        "unitPrice", line.unitPrice(),
                        "qty", line.qty(),
                        "discountAmount", line.discountAmount(),
                        "taxAmount", line.taxAmount(),
                        "lineTotal", line.lineTotal(),
                        "note", line.note()
                ))
                .toArray(org.springframework.jdbc.core.namedparam.SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                INSERT INTO pos.sale_order_line (
                    sale_order_id, line_number, product_id, product_code, product_name_snapshot,
                    unit_price, qty, discount_amount, tax_amount, line_total, note, created_at, updated_at
                ) VALUES (
                    :saleOrderId, :lineNumber, :productId, :productCode, :productNameSnapshot,
                    :unitPrice, :qty, :discountAmount, :taxAmount, :lineTotal, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, batchParams);
    }

    public Map<String, Object> getSaleSnapshot(NamedParameterJdbcTemplate jdbcTemplate, Long saleOrderId) {
        return jdbcTemplate.query("""
                SELECT order_snapshot
                FROM pos.sale_snapshot
                WHERE sale_order_id = :saleOrderId
                """, PosSql.params("saleOrderId", saleOrderId), rs -> {
            if (!rs.next()) return null;
            return rs.getObject("order_snapshot", Map.class);
        });
    }

    public void refreshPaymentStatus(NamedParameterJdbcTemplate jdbcTemplate, Long saleOrderId) {
        BigDecimal successAmount = successfulPaymentTotal(jdbcTemplate, saleOrderId);
        BigDecimal totalAmount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM pos.sale_order WHERE id = :id",
                PosSql.params("id", saleOrderId),
                BigDecimal.class
        );
        String paymentStatus = successAmount.compareTo(BigDecimal.ZERO) == 0
                ? SaleOrderPaymentStatus.UNPAID.name()
                : successAmount.compareTo(totalAmount) >= 0
                ? SaleOrderPaymentStatus.PAID.name()
                : SaleOrderPaymentStatus.PARTIALLY_PAID.name();
        jdbcTemplate.update("""
                UPDATE pos.sale_order
                SET payment_status = :paymentStatus, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, PosSql.params("paymentStatus", paymentStatus, "id", saleOrderId));
    }

    public PosSessionResponse mapSession(SessionRecord session) {
        return new PosSessionResponse(
                session.id(),
                session.sessionCode(),
                session.regionId(),
                session.outletId(),
                session.terminalId(),
                session.currencyCode(),
                session.cashierUserId(),
                session.managerUserId(),
                session.businessDate(),
                session.status(),
                session.note(),
                session.openedAt(),
                session.closedAt(),
                session.reconciledAt(),
                session.expectedCashAmount(),
                session.countedCashAmount(),
                session.discrepancyAmount()
        );
    }

    public SaleOrderResponse mapOrder(NamedParameterJdbcTemplate jdbcTemplate, OrderRecord order) {
        return mapOrder(order, queryOrderLines(jdbcTemplate, order.id()), queryPayments(jdbcTemplate, order.id()));
    }

    public SaleOrderResponse mapOrder(
            OrderRecord order,
            List<SaleOrderLineResponse> lines,
            List<SalePaymentResponse> payments
    ) {
        return new SaleOrderResponse(
                order.id(),
                order.orderNumber(),
                order.regionId(),
                order.outletId(),
                order.posSessionId(),
                order.currencyCode(),
                order.orderType(),
                order.status(),
                order.paymentStatus(),
                order.subtotal(),
                order.discountAmount(),
                order.taxAmount(),
                order.totalAmount(),
                order.note(),
                order.createdAt(),
                order.completedAt(),
                lines,
                payments
        );
    }
}
