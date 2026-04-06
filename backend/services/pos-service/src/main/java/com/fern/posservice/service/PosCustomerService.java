package com.fern.posservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.posservice.dto.PosCommands.CreateCustomerRequest;
import com.fern.posservice.dto.PosCommands.UpdateCustomerRequest;
import com.fern.posservice.dto.PosResponses.CustomerResponse;
import com.fern.posservice.dto.PosResponses.LoyaltyTransactionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for customer CRUD and loyalty point management.
 *
 * <p>Customer data is <em>global</em> — not shard-specific — so all operations
 * use the root (non-sharded) JDBC template resolved via {@code RouteKey.of(0L, 0L)}.
 */
@Service
public class PosCustomerService {
    private static final int MAX_PAGE_SIZE = 200;
    /** 1 loyalty point per 10,000 VND spent. */
    private static final BigDecimal LOYALTY_EARN_DIVISOR = new BigDecimal("10000");

    private final PosAuthorizer posAuthorizer;
    private final PosReferenceCodeGenerator codeGenerator;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;

    public PosCustomerService(
            PosAuthorizer posAuthorizer,
            PosReferenceCodeGenerator codeGenerator,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver
    ) {
        this.posAuthorizer = posAuthorizer;
        this.codeGenerator = codeGenerator;
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
    }

    public CustomerResponse createCustomer(FernPrincipal principal, CreateCustomerRequest request) {
        posAuthorizer.requirePermission(principal, PermissionCodes.POS_CUSTOMER_WRITE);
        String gender = resolveGender(request.gender());
        NamedParameterJdbcTemplate jdbcTemplate = rootJdbcTemplate();
        Long id = PosSql.insertForId(jdbcTemplate, """
                INSERT INTO pos.customer (
                    customer_code, full_name, phone, email, dob, gender, status, note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :customerCode, :fullName, :phone, :email, :dob, :gender, 'ACTIVE', :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PosSql.params(
                "customerCode", codeGenerator.nextCustomerCode(),
                "fullName", request.fullName(),
                "phone", request.phone(),
                "email", request.email(),
                "dob", request.dob(),
                "gender", gender,
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        return getCustomerById(id);
    }

    public CustomerResponse updateCustomer(FernPrincipal principal, Long id, UpdateCustomerRequest request) {
        posAuthorizer.requirePermission(principal, PermissionCodes.POS_CUSTOMER_WRITE);
        requireCustomerExists(id);
        String gender = request.gender() != null ? resolveGender(request.gender()) : null;
        String status = request.status() != null ? resolveStatus(request.status()) : null;
        NamedParameterJdbcTemplate jdbcTemplate = rootJdbcTemplate();
        int updated = jdbcTemplate.update("""
                UPDATE pos.customer
                SET full_name = COALESCE(:fullName, full_name),
                    phone = COALESCE(:phone, phone),
                    email = COALESCE(:email, email),
                    dob = COALESCE(:dob, dob),
                    gender = COALESCE(:gender, gender),
                    status = COALESCE(:status, status),
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, PosSql.params(
                "fullName", request.fullName(),
                "phone", request.phone(),
                "email", request.email(),
                "dob", request.dob(),
                "gender", gender,
                "status", status,
                "note", request.note(),
                "id", id
        ));
        if (updated != 1) {
            throw new ResourceNotFoundException("Customer not found");
        }
        return getCustomerById(id);
    }

    public CustomerResponse getCustomer(FernPrincipal principal, Long id) {
        posAuthorizer.requirePermission(principal, PermissionCodes.POS_CUSTOMER_READ);
        return getCustomerById(id);
    }

    public List<CustomerResponse> searchCustomers(FernPrincipal principal, String query, int limit) {
        posAuthorizer.requirePermission(principal, PermissionCodes.POS_CUSTOMER_READ);
        int clampedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        String trimmed = query == null ? "" : query.trim().toLowerCase();
        // Use prefix-only matching (query%) to allow B-tree index usage.
        // Leading-wildcard (%query%) would force a full table scan.
        String pattern = trimmed + "%";
        return rootJdbcTemplate().query("""
                SELECT id, customer_code, full_name, phone, email, dob, gender, loyalty_tier, loyalty_points,
                       total_spend, visit_count, status, note, created_at
                FROM pos.customer
                WHERE status != 'BLOCKED'
                  AND (LOWER(full_name) LIKE :pattern
                       OR LOWER(phone) LIKE :pattern
                       OR LOWER(customer_code) LIKE :pattern
                       OR LOWER(email) LIKE :pattern)
                ORDER BY full_name
                LIMIT :limit
                """, PosSql.params("pattern", pattern, "limit", clampedLimit), (rs, rowNum) -> mapCustomer(rs));
    }

    public List<CustomerResponse> listCustomers(FernPrincipal principal, String status, String loyaltyTier, int limit) {
        posAuthorizer.requirePermission(principal, PermissionCodes.POS_CUSTOMER_READ);
        int clampedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        StringBuilder sql = new StringBuilder("""
                SELECT id, customer_code, full_name, phone, email, dob, gender, loyalty_tier, loyalty_points,
                       total_spend, visit_count, status, note, created_at
                FROM pos.customer
                WHERE 1=1
                """);
        var params = PosSql.params("limit", clampedLimit);
        if (status != null && !status.isBlank()) {
            sql.append("\n  AND status = :status");
            params.addValue("status", status.toUpperCase());
        }
        if (loyaltyTier != null && !loyaltyTier.isBlank()) {
            sql.append("\n  AND loyalty_tier = :loyaltyTier");
            params.addValue("loyaltyTier", loyaltyTier.toUpperCase());
        }
        sql.append("\nORDER BY full_name\nLIMIT :limit");
        return rootJdbcTemplate().query(sql.toString(), params, (rs, rowNum) -> mapCustomer(rs));
    }

    public List<LoyaltyTransactionResponse> getLoyaltyTransactions(FernPrincipal principal, Long customerId, int limit) {
        posAuthorizer.requirePermission(principal, PermissionCodes.POS_CUSTOMER_READ);
        requireCustomerExists(customerId);
        int clampedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        return rootJdbcTemplate().query("""
                SELECT id, customer_id, sale_order_id, outlet_id, txn_type, points, balance_after, description, created_at
                FROM pos.loyalty_transaction
                WHERE customer_id = :customerId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """, PosSql.params("customerId", customerId, "limit", clampedLimit),
                (rs, rowNum) -> new LoyaltyTransactionResponse(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getObject("sale_order_id", Long.class),
                        rs.getObject("outlet_id", Long.class),
                        rs.getString("txn_type"),
                        rs.getInt("points"),
                        rs.getLong("balance_after"),
                        rs.getString("description"),
                        PosSql.instant(rs, "created_at")
                ));
    }

    /**
     * Accrues a customer visit after a sale order is completed.
     * Increments visit_count, adds to total_spend, earns loyalty points, and auto-upgrades tier.
     *
     * <p>This overload accepts an explicit {@link NamedParameterJdbcTemplate} so callers that
     * operate on a shard-specific transaction can pass their own template. Customer data lives
     * on the root shard, so the provided template <strong>must</strong> target the root database.
     * If {@code null}, the root JDBC template is used (standalone / non-sharded callers).
     *
     * @param jdbcTemplate the JDBC template to use (pass {@code null} for default root template)
     */
    @Transactional
    public void accrueVisit(NamedParameterJdbcTemplate jdbcTemplate, Long customerId, Long saleOrderId, Long outletId, BigDecimal totalAmount) {
        if (customerId == null) {
            return;
        }
        NamedParameterJdbcTemplate tpl = jdbcTemplate != null ? jdbcTemplate : rootJdbcTemplate();
        int earnedPoints = totalAmount
                .divide(LOYALTY_EARN_DIVISOR, 0, RoundingMode.FLOOR)
                .intValue();
        // Update customer aggregates
        tpl.update("""
                UPDATE pos.customer
                SET visit_count = visit_count + 1,
                    total_spend = total_spend + :totalAmount,
                    loyalty_points = loyalty_points + :earnedPoints,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, PosSql.params(
                "totalAmount", totalAmount,
                "earnedPoints", earnedPoints,
                "id", customerId
        ));
        // Insert loyalty earn transaction
        if (earnedPoints > 0) {
            // Read current balance after the update above
            Long currentPoints = tpl.queryForObject(
                    "SELECT loyalty_points FROM pos.customer WHERE id = :id",
                    PosSql.params("id", customerId),
                    Long.class
            );
            tpl.update("""
                    INSERT INTO pos.loyalty_transaction (
                        customer_id, sale_order_id, outlet_id, txn_type, points, balance_after, description, created_at
                    ) VALUES (
                        :customerId, :saleOrderId, :outletId, 'EARN', :points, :balanceAfter, :description, CURRENT_TIMESTAMP
                    )
                    """, PosSql.params(
                    "customerId", customerId,
                    "saleOrderId", saleOrderId,
                    "outletId", outletId,
                    "points", earnedPoints,
                    "balanceAfter", currentPoints,
                    "description", "Earned from order completion"
            ));
        }
        // M-01: Auto-upgrade loyalty tier based on total_spend thresholds
        upgradeLoyaltyTier(tpl, customerId);
    }

    /**
     * Convenience overload that uses the root JDBC template.
     * Use this when calling from outside a shard-specific transaction.
     */
    @Transactional
    public void accrueVisit(Long customerId, Long saleOrderId, Long outletId, BigDecimal totalAmount) {
        accrueVisit(null, customerId, saleOrderId, outletId, totalAmount);
    }

    /**
     * Auto-upgrades loyalty tier based on cumulative total_spend.
     * Tiers: BRONZE (default) → SILVER (≥5M VND) → GOLD (≥20M VND) → PLATINUM (≥50M VND).
     */
    private void upgradeLoyaltyTier(NamedParameterJdbcTemplate tpl, Long customerId) {
        BigDecimal totalSpend = tpl.queryForObject(
                "SELECT total_spend FROM pos.customer WHERE id = :id",
                PosSql.params("id", customerId),
                BigDecimal.class
        );
        if (totalSpend == null) {
            return;
        }
        String newTier;
        if (totalSpend.compareTo(new BigDecimal("50000000")) >= 0) {
            newTier = "PLATINUM";
        } else if (totalSpend.compareTo(new BigDecimal("20000000")) >= 0) {
            newTier = "GOLD";
        } else if (totalSpend.compareTo(new BigDecimal("5000000")) >= 0) {
            newTier = "SILVER";
        } else {
            newTier = "BRONZE";
        }
        tpl.update("""
                UPDATE pos.customer
                SET loyalty_tier = :newTier, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND (loyalty_tier IS NULL OR loyalty_tier != :newTier)
                """, PosSql.params("newTier", newTier, "id", customerId));
    }

    /**
     * Validates that a customer exists and is active.
     * Called during order creation/update when customerId is specified.
     */
    public void requireActiveCustomer(Long customerId) {
        if (customerId == null) {
            return;
        }
        String status = rootJdbcTemplate().query("""
                SELECT status FROM pos.customer WHERE id = :id
                """, PosSql.params("id", customerId), rs -> rs.next() ? rs.getString("status") : null);
        if (status == null) {
            throw new ResourceNotFoundException("Customer not found");
        }
        if (!"ACTIVE".equals(status)) {
            throw new BadRequestException("Customer is not active");
        }
    }

    private CustomerResponse getCustomerById(Long id) {
        CustomerResponse customer = rootJdbcTemplate().query("""
                SELECT id, customer_code, full_name, phone, email, dob, gender, loyalty_tier, loyalty_points,
                       total_spend, visit_count, status, note, created_at
                FROM pos.customer
                WHERE id = :id
                """, PosSql.params("id", id), (rs) -> rs.next() ? mapCustomer(rs) : null);
        if (customer == null) {
            throw new ResourceNotFoundException("Customer not found");
        }
        return customer;
    }

    private void requireCustomerExists(Long id) {
        Long exists = rootJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM pos.customer WHERE id = :id",
                PosSql.params("id", id),
                Long.class
        );
        if (exists == null || exists == 0) {
            throw new ResourceNotFoundException("Customer not found");
        }
    }

    private NamedParameterJdbcTemplate rootJdbcTemplate() {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(0L, 0L))).jdbc();
    }

    private CustomerResponse mapCustomer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CustomerResponse(
                rs.getLong("id"),
                rs.getString("customer_code"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getObject("dob", LocalDate.class),
                rs.getString("gender"),
                rs.getString("loyalty_tier"),
                rs.getLong("loyalty_points"),
                rs.getBigDecimal("total_spend"),
                rs.getInt("visit_count"),
                rs.getString("status"),
                rs.getString("note"),
                PosSql.instant(rs, "created_at")
        );
    }

    private String resolveGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = gender.trim().toUpperCase();
        return switch (normalized) {
            case "MALE", "FEMALE", "OTHER", "UNKNOWN" -> normalized;
            default -> throw new BadRequestException("Invalid gender: " + gender + ". Must be MALE, FEMALE, OTHER, or UNKNOWN");
        };
    }

    private String resolveStatus(String status) {
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "ACTIVE", "INACTIVE", "BLOCKED" -> normalized;
            default -> throw new BadRequestException("Invalid status: " + status + ". Must be ACTIVE, INACTIVE, or BLOCKED");
        };
    }
}
