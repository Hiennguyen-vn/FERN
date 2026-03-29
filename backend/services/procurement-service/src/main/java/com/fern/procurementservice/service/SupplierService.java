package com.fern.procurementservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierUpsertRequest;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {
    private final ProcurementJdbcRepository procurementJdbcRepository;
    private final ProcurementAuthorizer procurementAuthorizer;
    private final Clock clock;

    public SupplierService(
            ProcurementJdbcRepository procurementJdbcRepository,
            ProcurementAuthorizer procurementAuthorizer,
            Clock clock
    ) {
        this.procurementJdbcRepository = procurementJdbcRepository;
        this.procurementAuthorizer = procurementAuthorizer;
        this.clock = clock;
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<SupplierResponse> listSuppliers(FernPrincipal principal) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_SUPPLIER_READ);
        return masterJdbcTemplate().query("""
                SELECT id, supplier_code, name, tax_code, email, phone, address, default_region_id, status, approved_at
                FROM procurement_master.supplier
                WHERE deleted_at IS NULL
                ORDER BY supplier_code
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(), (rs, rowNum) -> new SupplierResponse(
                rs.getLong("id"),
                rs.getString("supplier_code"),
                rs.getString("name"),
                rs.getString("tax_code"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getObject("default_region_id", Long.class),
                rs.getString("status"),
                instant(rs, "approved_at")
        ));
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public SupplierResponse createSupplier(FernPrincipal principal, SupplierUpsertRequest request) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_SUPPLIER_WRITE);
        Long id = insertForId(masterJdbcTemplate(), """
                INSERT INTO procurement_master.supplier (
                    supplier_code, name, tax_code, email, phone, address, default_region_id, status, deleted_at, created_at, updated_at
                ) VALUES (
                    :supplierCode, :name, :taxCode, :email, :phone, :address, :defaultRegionId, :status, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "supplierCode", request.supplierCode(),
                "name", request.name(),
                "taxCode", request.taxCode(),
                "email", request.email(),
                "phone", request.phone(),
                "address", request.address(),
                "defaultRegionId", request.defaultRegionId(),
                "status", request.status() == null ? "INACTIVE" : request.status()
        ));
        return getSupplier(id);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public SupplierResponse updateSupplier(FernPrincipal principal, Long id, SupplierUpsertRequest request) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_SUPPLIER_WRITE);
        requireSupplier(id);
        masterJdbcTemplate().update("""
                UPDATE procurement_master.supplier
                SET supplier_code = :supplierCode,
                    name = :name,
                    tax_code = :taxCode,
                    email = :email,
                    phone = :phone,
                    address = :address,
                    default_region_id = :defaultRegionId,
                    status = :status,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "supplierCode", request.supplierCode(),
                "name", request.name(),
                "taxCode", request.taxCode(),
                "email", request.email(),
                "phone", request.phone(),
                "address", request.address(),
                "defaultRegionId", request.defaultRegionId(),
                "status", request.status() == null ? "INACTIVE" : request.status(),
                "id", id
        ));
        return getSupplier(id);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public SupplierResponse activateSupplier(FernPrincipal principal, Long id) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_SUPPLIER_WRITE);
        requireSupplier(id);
        masterJdbcTemplate().update("""
                UPDATE procurement_master.supplier
                SET status = 'ACTIVE',
                    approved_by_user_id = :approvedByUserId,
                    approved_at = :approvedAt,
                    activated_by_user_id = :activatedByUserId,
                    activated_at = :activatedAt,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "approvedByUserId", principal.userId(),
                "approvedAt", Instant.now(clock),
                "activatedByUserId", principal.userId(),
                "activatedAt", Instant.now(clock),
                "id", id
        ));
        return getSupplier(id);
    }

    private NamedParameterJdbcTemplate masterJdbcTemplate() {
        return procurementJdbcRepository.masterJdbcTemplate();
    }

    private MapSqlParameterSource params(Object... values) {
        return procurementJdbcRepository.params(values);
    }

    private Long insertForId(NamedParameterJdbcTemplate template, String sql, MapSqlParameterSource parameters) {
        return procurementJdbcRepository.insertForId(template, sql, parameters);
    }

    private SupplierResponse getSupplier(Long id) {
        return procurementJdbcRepository.getSupplier(id);
    }

    private SupplierRecord requireSupplier(Long id) {
        return procurementJdbcRepository.requireSupplier(id);
    }

    private Instant instant(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        return procurementJdbcRepository.instant(resultSet, column);
    }
}
