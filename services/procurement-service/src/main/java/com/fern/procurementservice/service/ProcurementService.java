package com.fern.procurementservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.observability.CorrelationId;
import com.fern.procurementservice.dto.ProcurementCommands.CreateGoodsReceiptRequest;
import com.fern.procurementservice.dto.ProcurementCommands.CreatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierInvoiceRequest;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierPaymentRequest;
import com.fern.procurementservice.dto.ProcurementCommands.GoodsReceiptLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.PaymentAllocationInput;
import com.fern.procurementservice.dto.ProcurementCommands.PurchaseOrderLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierInvoiceLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierUpsertRequest;
import com.fern.procurementservice.dto.ProcurementCommands.UpdatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptLineResponse;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptResponse;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderLineResponse;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceLineResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentAllocationResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ProcurementService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final ProcurementAuthorizer procurementAuthorizer;
    private final ProcurementOrgClient procurementOrgClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProcurementService(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            ProcurementAuthorizer procurementAuthorizer,
            ProcurementOrgClient procurementOrgClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.procurementAuthorizer = procurementAuthorizer;
        this.procurementOrgClient = procurementOrgClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<SupplierResponse> listSuppliers(FernPrincipal principal) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_SUPPLIER_READ);
        return masterJdbcTemplate.query("""
                SELECT id, supplier_code, name, tax_code, email, phone, address, default_region_id, status, approved_at
                FROM procurement_master.supplier
                WHERE deleted_at IS NULL
                ORDER BY supplier_code
                """, new MapSqlParameterSource(), (rs, rowNum) -> new SupplierResponse(
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
        Long id = insertForId(masterJdbcTemplate, """
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
        masterJdbcTemplate.update("""
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
        masterJdbcTemplate.update("""
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

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(FernPrincipal principal, CreatePurchaseOrderRequest request) {
        procurementAuthorizer.requireOutletPermission(principal, request.outletId(), PermissionCodes.PROCUREMENT_PO_CREATE);
        ProcurementOrgClient.OutletRoute outlet = procurementOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new BadRequestException("Region does not match the outlet route");
        }
        SupplierRecord supplier = requireActiveApprovedSupplier(request.supplierId());
        PurchaseOrderTotals totals = calculatePurchaseOrderTotals(request.lines());
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO procurement.purchase_order (
                    po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status,
                    subtotal_amount, tax_amount, total_amount, note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :poNumber, :regionId, :outletId, :supplierId, :orderDate, :expectedDeliveryDate, 'DRAFT',
                    :subtotalAmount, :taxAmount, :totalAmount, :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "poNumber", "PO-" + Instant.now(clock).toEpochMilli(),
                "regionId", outlet.regionId(),
                "outletId", request.outletId(),
                "supplierId", supplier.id(),
                "orderDate", request.orderDate(),
                "expectedDeliveryDate", request.expectedDeliveryDate(),
                "subtotalAmount", totals.subtotal(),
                "taxAmount", totals.taxAmount(),
                "totalAmount", totals.totalAmount(),
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        replacePurchaseOrderLines(id, request.lines());
        return getPurchaseOrder(principal, id);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrder(FernPrincipal principal, Long id) {
        PurchaseOrderRecord record = requirePurchaseOrder(id);
        procurementAuthorizer.requireRouteRead(principal, record.regionId(), record.outletId(), PermissionCodes.PROCUREMENT_PO_READ);
        return mapPurchaseOrder(record);
    }

    @Transactional
    public PurchaseOrderResponse updatePurchaseOrder(FernPrincipal principal, Long id, UpdatePurchaseOrderRequest request) {
        PurchaseOrderRecord record = requirePurchaseOrderForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_PO_UPDATE);
        if (!"DRAFT".equals(record.status())) {
            throw new ConflictException("Only draft purchase orders can be updated");
        }
        PurchaseOrderTotals totals = calculatePurchaseOrderTotals(request.lines());
        int updated = jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET expected_delivery_date = :expectedDeliveryDate,
                    subtotal_amount = :subtotalAmount,
                    tax_amount = :taxAmount,
                    total_amount = :totalAmount,
                    note = :note,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'DRAFT'
                """, params(
                "expectedDeliveryDate", request.expectedDeliveryDate(),
                "subtotalAmount", totals.subtotal(),
                "taxAmount", totals.taxAmount(),
                "totalAmount", totals.totalAmount(),
                "note", request.note(),
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only draft purchase orders can be updated");
        }
        replacePurchaseOrderLines(id, request.lines());
        return getPurchaseOrder(principal, id);
    }

    @Transactional
    public PurchaseOrderResponse submitPurchaseOrder(FernPrincipal principal, Long id) {
        PurchaseOrderRecord record = requirePurchaseOrderForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_PO_SUBMIT);
        ensureStatus(record.status(), "DRAFT", "Only draft purchase orders can be submitted");
        requireActiveApprovedSupplier(record.supplierId());
        int updated = jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET status = 'SUBMITTED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'DRAFT'
                """, params("id", id));
        if (updated != 1) {
            throw new ConflictException("Only draft purchase orders can be submitted");
        }
        return getPurchaseOrder(principal, id);
    }

    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(FernPrincipal principal, Long id) {
        PurchaseOrderRecord record = requirePurchaseOrderForUpdate(id);
        procurementAuthorizer.requireRegionPermission(principal, record.regionId(), PermissionCodes.PROCUREMENT_PO_APPROVE);
        ensureStatus(record.status(), "SUBMITTED", "Only submitted purchase orders can be approved");
        int updated = jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET status = 'APPROVED', approved_by_user_id = :approvedByUserId, approved_at = :approvedAt, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'SUBMITTED'
                """, params("approvedByUserId", principal.userId(), "approvedAt", Instant.now(clock), "id", id));
        if (updated != 1) {
            throw new ConflictException("Only submitted purchase orders can be approved");
        }
        return getPurchaseOrder(principal, id);
    }

    @Transactional
    public PurchaseOrderResponse issuePurchaseOrder(FernPrincipal principal, Long id) {
        PurchaseOrderRecord record = requirePurchaseOrderForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_PO_ISSUE);
        ensureStatus(record.status(), "APPROVED", "Only approved purchase orders can be issued");
        int updated = jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET status = 'ORDERED',
                    issued_by_user_id = :issuedByUserId,
                    issued_at = :issuedAt,
                    commercial_snapshot = CAST(:commercialSnapshot AS jsonb),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'APPROVED'
                """, params(
                "issuedByUserId", principal.userId(),
                "issuedAt", Instant.now(clock),
                "commercialSnapshot", toJson(mapPurchaseOrder(record)),
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only approved purchase orders can be issued");
        }
        return getPurchaseOrder(principal, id);
    }

    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(FernPrincipal principal, Long id) {
        PurchaseOrderRecord record = requirePurchaseOrderForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_PO_CANCEL);
        if (List.of("PARTIALLY_RECEIVED", "COMPLETED", "CLOSED", "CANCELLED").contains(record.status())) {
            throw new ConflictException("This purchase order can no longer be cancelled");
        }
        int updated = jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :status
                """, params("id", id, "status", record.status()));
        if (updated != 1) {
            throw new ConflictException("This purchase order can no longer be cancelled");
        }
        return getPurchaseOrder(principal, id);
    }

    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(FernPrincipal principal, CreateGoodsReceiptRequest request) {
        PurchaseOrderRecord purchaseOrder = requirePurchaseOrder(request.purchaseOrderId());
        procurementAuthorizer.requireOutletPermission(principal, purchaseOrder.outletId(), PermissionCodes.PROCUREMENT_GR_CREATE);
        if (!List.of("APPROVED", "ORDERED", "PARTIALLY_RECEIVED").contains(purchaseOrder.status())) {
            throw new ConflictException("Goods receipts can only be created from issued or partially received purchase orders");
        }
        BigDecimal totalAmount = calculateGoodsReceiptTotal(request.lines());
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO procurement.goods_receipt (
                    receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date, status,
                    total_amount, supplier_lot_number, note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :receiptNumber, :purchaseOrderId, :regionId, :outletId, :supplierId, :receiptTime, :businessDate, 'DRAFT',
                    :totalAmount, :supplierLotNumber, :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "receiptNumber", "GR-" + Instant.now(clock).toEpochMilli(),
                "purchaseOrderId", purchaseOrder.id(),
                "regionId", purchaseOrder.regionId(),
                "outletId", purchaseOrder.outletId(),
                "supplierId", purchaseOrder.supplierId(),
                "receiptTime", request.receiptTime(),
                "businessDate", request.businessDate(),
                "totalAmount", totalAmount,
                "supplierLotNumber", request.supplierLotNumber(),
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        insertGoodsReceiptLines(id, request.lines());
        return getGoodsReceipt(principal, id);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse getGoodsReceipt(FernPrincipal principal, Long id) {
        GoodsReceiptRecord record = requireGoodsReceipt(id);
        procurementAuthorizer.requireRouteRead(principal, record.regionId(), record.outletId(), PermissionCodes.PROCUREMENT_GR_READ);
        return mapGoodsReceipt(record);
    }

    @Transactional
    public GoodsReceiptResponse receiveGoodsReceipt(FernPrincipal principal, Long id) {
        GoodsReceiptRecord record = requireGoodsReceiptForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_GR_CREATE);
        ensureStatus(record.status(), "DRAFT", "Only draft goods receipts can be received");
        int updated = jdbcTemplate.update("""
                UPDATE procurement.goods_receipt
                SET status = 'RECEIVED', received_by_user_id = :receivedByUserId, received_at = :receivedAt, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'DRAFT'
                """, params("receivedByUserId", principal.userId(), "receivedAt", Instant.now(clock), "id", id));
        if (updated != 1) {
            throw new ConflictException("Only draft goods receipts can be received");
        }
        return getGoodsReceipt(principal, id);
    }

    @Transactional
    public GoodsReceiptResponse postGoodsReceipt(FernPrincipal principal, Long id, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        GoodsReceiptRecord record = requireGoodsReceiptForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_GR_POST);
        Long duplicateId = jdbcTemplate.query("""
                SELECT id FROM procurement.goods_receipt WHERE posted_idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
        if (duplicateId != null) {
            return getGoodsReceipt(principal, duplicateId);
        }
        ensureStatus(record.status(), "RECEIVED", "Only received goods receipts can be posted");
        int updated = jdbcTemplate.update("""
                UPDATE procurement.goods_receipt
                SET status = 'POSTED',
                    posted_by_user_id = :postedByUserId,
                    posted_at = :postedAt,
                    posted_idempotency_key = :postedIdempotencyKey,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'RECEIVED'
                """, params(
                "postedByUserId", principal.userId(),
                "postedAt", Instant.now(clock),
                "postedIdempotencyKey", idempotencyKey,
                "id", id
        ));
        if (updated != 1) {
            throw new ConflictException("Only received goods receipts can be posted");
        }
        updatePurchaseOrderReceiptProgress(record.purchaseOrderId(), id);
        enqueueGoodsReceiptPostedEvent(record, principal);
        return getGoodsReceipt(principal, id);
    }

    @Transactional
    public GoodsReceiptResponse cancelGoodsReceipt(FernPrincipal principal, Long id) {
        GoodsReceiptRecord record = requireGoodsReceiptForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_GR_CANCEL);
        if (!List.of("DRAFT", "RECEIVED").contains(record.status())) {
            throw new ConflictException("Only draft or received goods receipts can be cancelled");
        }
        int updated = jdbcTemplate.update("""
                UPDATE procurement.goods_receipt
                SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :status
                """, params("id", id, "status", record.status()));
        if (updated != 1) {
            throw new ConflictException("Only draft or received goods receipts can be cancelled");
        }
        return getGoodsReceipt(principal, id);
    }

    @Transactional
    public SupplierInvoiceResponse createSupplierInvoice(FernPrincipal principal, CreateSupplierInvoiceRequest request) {
        procurementAuthorizer.requireRegionPermission(principal, request.regionId(), PermissionCodes.PROCUREMENT_INVOICE_REVIEW);
        SupplierRecord supplier = requireActiveApprovedSupplier(request.supplierId());
        ProcurementOrgClient.OutletRoute outlet = procurementOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new ConflictException("Outlet route does not match requested region");
        }
        BigDecimal subtotal = request.lines().stream()
                .map(line -> line.lineTotal().subtract(line.taxAmount() == null ? BigDecimal.ZERO : line.taxAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxAmount = request.lines().stream()
                .map(line -> line.taxAmount() == null ? BigDecimal.ZERO : line.taxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = request.lines().stream().map(SupplierInvoiceLineInput::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO procurement.supplier_invoice (
                    invoice_number, supplier_id, region_id, outlet_id, currency_code, invoice_date, due_date, subtotal, tax_amount,
                    total_amount, status, note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :invoiceNumber, :supplierId, :regionId, :outletId, :currencyCode, :invoiceDate, :dueDate, :subtotal, :taxAmount,
                    :totalAmount, 'RECEIVED', :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "invoiceNumber", request.invoiceNumber(),
                "supplierId", supplier.id(),
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "currencyCode", request.currencyCode(),
                "invoiceDate", request.invoiceDate(),
                "dueDate", request.dueDate(),
                "subtotal", subtotal,
                "taxAmount", taxAmount,
                "totalAmount", totalAmount,
                "note", request.note(),
                "createdByUserId", principal.userId()
        ));
        insertInvoiceLines(id, request.lines());
        return getSupplierInvoice(principal, id);
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceResponse getSupplierInvoice(FernPrincipal principal, Long id) {
        SupplierInvoiceRecord record = requireSupplierInvoice(id);
        procurementAuthorizer.requireRouteRead(principal, record.regionId(), record.outletId(), PermissionCodes.PROCUREMENT_INVOICE_READ);
        return mapSupplierInvoice(record);
    }

    @Transactional
    public SupplierInvoiceResponse approveSupplierInvoice(FernPrincipal principal, Long id) {
        SupplierInvoiceRecord record = requireSupplierInvoiceForUpdate(id);
        procurementAuthorizer.requireRegionPermission(principal, record.regionId(), PermissionCodes.PROCUREMENT_INVOICE_APPROVE);
        if (!List.of("DRAFT", "RECEIVED", "MATCHED").contains(record.status())) {
            throw new ConflictException("Only received or matched supplier invoices can be approved");
        }
        int updated = jdbcTemplate.update("""
                UPDATE procurement.supplier_invoice
                SET status = 'APPROVED', approved_by_user_id = :approvedByUserId, approved_at = :approvedAt, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status IN ('DRAFT', 'RECEIVED', 'MATCHED')
                """, params("approvedByUserId", principal.userId(), "approvedAt", Instant.now(clock), "id", id));
        if (updated != 1) {
            throw new ConflictException("Only received or matched supplier invoices can be approved");
        }
        return getSupplierInvoice(principal, id);
    }

    @Transactional
    public SupplierInvoiceResponse disputeSupplierInvoice(FernPrincipal principal, Long id) {
        SupplierInvoiceRecord record = requireSupplierInvoiceForUpdate(id);
        procurementAuthorizer.requireRegionPermission(principal, record.regionId(), PermissionCodes.PROCUREMENT_INVOICE_DISPUTE);
        if ("CANCELLED".equals(record.status())) {
            throw new ConflictException("Cancelled invoices cannot be disputed");
        }
        int updated = jdbcTemplate.update("""
                UPDATE procurement.supplier_invoice
                SET status = 'DISPUTED', updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status <> 'CANCELLED'
                """, params("id", id));
        if (updated != 1) {
            throw new ConflictException("Cancelled invoices cannot be disputed");
        }
        return getSupplierInvoice(principal, id);
    }

    @Transactional
    public SupplierPaymentResponse createSupplierPayment(FernPrincipal principal, String idempotencyKey, CreateSupplierPaymentRequest request) {
        requireIdempotencyKey(idempotencyKey);
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_PAYMENT_RECORD);
        Long existingPaymentId = jdbcTemplate.query("""
                SELECT id FROM procurement.supplier_payment WHERE idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
        if (existingPaymentId != null) {
            return getSupplierPayment(existingPaymentId);
        }
        requireActiveApprovedSupplier(request.supplierId());
        BigDecimal allocatedTotal = request.invoiceAllocations().stream()
                .map(PaymentAllocationInput::allocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedTotal.compareTo(request.amount()) > 0) {
            throw new ConflictException("Supplier payment allocations cannot exceed the payment amount");
        }
        Map<Long, BigDecimal> requestedAllocationByInvoice = request.invoiceAllocations().stream()
                .collect(Collectors.toMap(
                        PaymentAllocationInput::supplierInvoiceId,
                        PaymentAllocationInput::allocatedAmount,
                        BigDecimal::add,
                        LinkedHashMap::new
                ));
        for (Long invoiceId : requestedAllocationByInvoice.keySet().stream().sorted().toList()) {
            SupplierInvoiceRecord invoice = requireSupplierInvoiceForUpdate(invoiceId);
            procurementAuthorizer.requireRouteRead(
                    principal,
                    invoice.regionId(),
                    invoice.outletId(),
                    PermissionCodes.PROCUREMENT_PAYMENT_RECORD
            );
            if (!invoice.supplierId().equals(request.supplierId())) {
                throw new ConflictException("Supplier payment allocations must belong to the same supplier");
            }
            if (!"APPROVED".equals(invoice.status())) {
                throw new ConflictException("Only approved supplier invoices can be paid");
            }
            BigDecimal openAmount = invoice.totalAmount().subtract(invoiceAllocatedAmount(invoice.id()));
            if (requestedAllocationByInvoice.get(invoice.id()).compareTo(openAmount) > 0) {
                throw new ConflictException("Allocated amount exceeds the supplier invoice open amount");
            }
        }
        Long id = insertForId(jdbcTemplate, """
                INSERT INTO procurement.supplier_payment (
                    payment_number, supplier_id, currency_code, payment_method, amount, payment_time, transaction_ref,
                    note, created_by_user_id, created_at, updated_at, idempotency_key
                ) VALUES (
                    :paymentNumber, :supplierId, :currencyCode, :paymentMethod, :amount, :paymentTime, :transactionRef,
                    :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :idempotencyKey
                )
                """, params(
                "paymentNumber", "SP-" + Instant.now(clock).toEpochMilli(),
                "supplierId", request.supplierId(),
                "currencyCode", request.currencyCode(),
                "paymentMethod", request.paymentMethod(),
                "amount", request.amount(),
                "paymentTime", request.paymentTime(),
                "transactionRef", request.transactionRef(),
                "note", request.note(),
                "createdByUserId", principal.userId(),
                "idempotencyKey", idempotencyKey
        ));
        for (PaymentAllocationInput allocation : request.invoiceAllocations()) {
            jdbcTemplate.update("""
                    INSERT INTO procurement.supplier_payment_allocation (
                        supplier_payment_id, supplier_invoice_id, allocated_amount, note, created_at, updated_at
                    ) VALUES (
                        :supplierPaymentId, :supplierInvoiceId, :allocatedAmount, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "supplierPaymentId", id,
                    "supplierInvoiceId", allocation.supplierInvoiceId(),
                    "allocatedAmount", allocation.allocatedAmount(),
                    "note", allocation.note()
            ));
        }
        enqueueSupplierPaymentRecordedEvent(id, principal);
        return getSupplierPayment(id);
    }

    private SupplierResponse getSupplier(Long id) {
        SupplierResponse response = masterJdbcTemplate.query("""
                SELECT id, supplier_code, name, tax_code, email, phone, address, default_region_id, status, approved_at
                FROM procurement_master.supplier
                WHERE id = :id AND deleted_at IS NULL
                """, params("id", id), rs -> rs.next() ? new SupplierResponse(
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
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Supplier not found");
        }
        return response;
    }

    private SupplierRecord requireSupplier(Long id) {
        SupplierResponse response = getSupplier(id);
        return new SupplierRecord(response.id(), response.status(), response.approvedAt());
    }

    private SupplierRecord requireActiveApprovedSupplier(Long id) {
        SupplierRecord supplier = requireSupplier(id);
        if (!"ACTIVE".equals(supplier.status()) || supplier.approvedAt() == null) {
            throw new ConflictException("Supplier must be active and approved before it can be referenced");
        }
        return supplier;
    }

    private PurchaseOrderRecord requirePurchaseOrder(Long id) {
        PurchaseOrderRecord record = jdbcTemplate.query("""
                SELECT id, po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status, subtotal_amount, tax_amount, total_amount, note, approved_at, issued_at
                FROM procurement.purchase_order
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new PurchaseOrderRecord(
                rs.getLong("id"),
                rs.getString("po_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                instant(rs, "approved_at"),
                instant(rs, "issued_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Purchase order not found");
        }
        return record;
    }

    private PurchaseOrderRecord requirePurchaseOrderForUpdate(Long id) {
        PurchaseOrderRecord record = jdbcTemplate.query("""
                SELECT id, po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status, subtotal_amount, tax_amount, total_amount, note, approved_at, issued_at
                FROM procurement.purchase_order
                WHERE id = :id
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? new PurchaseOrderRecord(
                rs.getLong("id"),
                rs.getString("po_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                instant(rs, "approved_at"),
                instant(rs, "issued_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Purchase order not found");
        }
        return record;
    }

    private GoodsReceiptRecord requireGoodsReceipt(Long id) {
        GoodsReceiptRecord record = jdbcTemplate.query("""
                SELECT id, receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                       status, total_amount, supplier_lot_number, note, received_at, posted_at
                FROM procurement.goods_receipt
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new GoodsReceiptRecord(
                rs.getLong("id"),
                rs.getString("receipt_number"),
                rs.getLong("purchase_order_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                instant(rs, "receipt_time"),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("supplier_lot_number"),
                rs.getString("note"),
                instant(rs, "received_at"),
                instant(rs, "posted_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Goods receipt not found");
        }
        return record;
    }

    private GoodsReceiptRecord requireGoodsReceiptForUpdate(Long id) {
        GoodsReceiptRecord record = jdbcTemplate.query("""
                SELECT id, receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                       status, total_amount, supplier_lot_number, note, received_at, posted_at
                FROM procurement.goods_receipt
                WHERE id = :id
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? new GoodsReceiptRecord(
                rs.getLong("id"),
                rs.getString("receipt_number"),
                rs.getLong("purchase_order_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                instant(rs, "receipt_time"),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("supplier_lot_number"),
                rs.getString("note"),
                instant(rs, "received_at"),
                instant(rs, "posted_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Goods receipt not found");
        }
        return record;
    }

    private SupplierInvoiceRecord requireSupplierInvoice(Long id) {
        SupplierInvoiceRecord record = jdbcTemplate.query("""
                SELECT id, supplier_id, region_id, outlet_id, currency_code, invoice_number, invoice_date, due_date,
                       subtotal, tax_amount, total_amount, status, note, approved_at
                FROM procurement.supplier_invoice
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new SupplierInvoiceRecord(
                rs.getLong("id"),
                rs.getLong("supplier_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("currency_code"),
                rs.getString("invoice_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getString("note"),
                instant(rs, "approved_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Supplier invoice not found");
        }
        return record;
    }

    private SupplierInvoiceRecord requireSupplierInvoiceForUpdate(Long id) {
        SupplierInvoiceRecord record = jdbcTemplate.query("""
                SELECT id, supplier_id, region_id, outlet_id, currency_code, invoice_number, invoice_date, due_date,
                       subtotal, tax_amount, total_amount, status, note, approved_at
                FROM procurement.supplier_invoice
                WHERE id = :id
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? new SupplierInvoiceRecord(
                rs.getLong("id"),
                rs.getLong("supplier_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("currency_code"),
                rs.getString("invoice_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getString("note"),
                instant(rs, "approved_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Supplier invoice not found");
        }
        return record;
    }

    private BigDecimal invoiceAllocatedAmount(Long supplierInvoiceId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocated_amount), 0)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = :supplierInvoiceId
                """, params("supplierInvoiceId", supplierInvoiceId), BigDecimal.class);
    }

    private PurchaseOrderResponse mapPurchaseOrder(PurchaseOrderRecord record) {
        List<PurchaseOrderLineResponse> lines = jdbcTemplate.query("""
                SELECT id, line_number, ingredient_id, uom_code, qty_ordered, qty_received, expected_unit_price, tax_percent, status, note
                FROM procurement.purchase_order_line
                WHERE purchase_order_id = :purchaseOrderId
                ORDER BY line_number
                """, params("purchaseOrderId", record.id()), (rs, rowNum) -> new PurchaseOrderLineResponse(
                rs.getLong("id"),
                rs.getInt("line_number"),
                rs.getLong("ingredient_id"),
                rs.getString("uom_code"),
                rs.getBigDecimal("qty_ordered"),
                rs.getBigDecimal("qty_received"),
                rs.getBigDecimal("expected_unit_price"),
                rs.getBigDecimal("tax_percent"),
                rs.getString("status"),
                rs.getString("note")
        ));
        return new PurchaseOrderResponse(
                record.id(),
                record.poNumber(),
                record.regionId(),
                record.outletId(),
                record.supplierId(),
                record.orderDate(),
                record.expectedDeliveryDate(),
                record.status(),
                record.subtotalAmount(),
                record.taxAmount(),
                record.totalAmount(),
                record.note(),
                record.approvedAt(),
                record.issuedAt(),
                lines
        );
    }

    private GoodsReceiptResponse mapGoodsReceipt(GoodsReceiptRecord record) {
        List<GoodsReceiptLineResponse> lines = jdbcTemplate.query("""
                SELECT id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost, line_total, note
                FROM procurement.goods_receipt_line
                WHERE goods_receipt_id = :goodsReceiptId
                ORDER BY id
                """, params("goodsReceiptId", record.id()), (rs, rowNum) -> new GoodsReceiptLineResponse(
                rs.getLong("id"),
                rs.getObject("purchase_order_line_id", Long.class),
                rs.getLong("ingredient_id"),
                rs.getString("uom_code"),
                rs.getBigDecimal("qty_received"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("line_total"),
                rs.getString("note")
        ));
        return new GoodsReceiptResponse(
                record.id(),
                record.receiptNumber(),
                record.purchaseOrderId(),
                record.regionId(),
                record.outletId(),
                record.supplierId(),
                record.receiptTime(),
                record.businessDate(),
                record.status(),
                record.totalAmount(),
                record.supplierLotNumber(),
                record.note(),
                record.receivedAt(),
                record.postedAt(),
                lines
        );
    }

    private SupplierInvoiceResponse mapSupplierInvoice(SupplierInvoiceRecord record) {
        List<SupplierInvoiceLineResponse> lines = jdbcTemplate.query("""
                SELECT id, line_number, line_type, goods_receipt_line_id, description, qty_invoiced, unit_price, tax_percent, tax_amount, line_total, note
                FROM procurement.supplier_invoice_line
                WHERE supplier_invoice_id = :supplierInvoiceId
                ORDER BY line_number
                """, params("supplierInvoiceId", record.id()), (rs, rowNum) -> new SupplierInvoiceLineResponse(
                rs.getLong("id"),
                rs.getInt("line_number"),
                rs.getString("line_type"),
                rs.getObject("goods_receipt_line_id", Long.class),
                rs.getString("description"),
                rs.getBigDecimal("qty_invoiced"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("tax_percent"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("line_total"),
                rs.getString("note")
        ));
        return new SupplierInvoiceResponse(
                record.id(),
                record.supplierId(),
                record.regionId(),
                record.outletId(),
                record.currencyCode(),
                record.invoiceNumber(),
                record.invoiceDate(),
                record.dueDate(),
                record.subtotal(),
                record.taxAmount(),
                record.totalAmount(),
                record.status(),
                record.note(),
                record.approvedAt(),
                lines
        );
    }

    private SupplierPaymentResponse getSupplierPayment(Long id) {
        SupplierPaymentResponse response = jdbcTemplate.query("""
                SELECT id, payment_number, supplier_id, currency_code, payment_method, amount, payment_time, transaction_ref, note
                FROM procurement.supplier_payment
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new SupplierPaymentResponse(
                rs.getLong("id"),
                rs.getString("payment_number"),
                rs.getLong("supplier_id"),
                rs.getString("currency_code"),
                rs.getString("payment_method"),
                rs.getBigDecimal("amount"),
                instant(rs, "payment_time"),
                rs.getString("transaction_ref"),
                rs.getString("note"),
                jdbcTemplate.query("""
                        SELECT supplier_invoice_id, allocated_amount, note
                        FROM procurement.supplier_payment_allocation
                        WHERE supplier_payment_id = :supplierPaymentId
                        ORDER BY supplier_invoice_id
                        """, params("supplierPaymentId", id), (allocationRs, rowNum) -> new SupplierPaymentAllocationResponse(
                        allocationRs.getLong("supplier_invoice_id"),
                        allocationRs.getBigDecimal("allocated_amount"),
                        allocationRs.getString("note")
                ))
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Supplier payment not found");
        }
        return response;
    }

    private void replacePurchaseOrderLines(Long purchaseOrderId, List<PurchaseOrderLineInput> lines) {
        jdbcTemplate.update("DELETE FROM procurement.purchase_order_line WHERE purchase_order_id = :purchaseOrderId", params("purchaseOrderId", purchaseOrderId));
        int lineNumber = 1;
        for (PurchaseOrderLineInput line : lines) {
            jdbcTemplate.update("""
                    INSERT INTO procurement.purchase_order_line (
                        purchase_order_id, line_number, ingredient_id, uom_code, qty_ordered, qty_received,
                        expected_unit_price, tax_percent, status, note, created_at, updated_at
                    ) VALUES (
                        :purchaseOrderId, :lineNumber, :ingredientId, :uomCode, :qtyOrdered, 0,
                        :expectedUnitPrice, :taxPercent, 'OPEN', :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "purchaseOrderId", purchaseOrderId,
                    "lineNumber", lineNumber++,
                    "ingredientId", line.ingredientId(),
                    "uomCode", line.uomCode(),
                    "qtyOrdered", line.qtyOrdered(),
                    "expectedUnitPrice", line.expectedUnitPrice(),
                    "taxPercent", line.taxPercent() == null ? BigDecimal.ZERO : line.taxPercent(),
                    "note", line.note()
            ));
        }
    }

    private void insertGoodsReceiptLines(Long goodsReceiptId, List<GoodsReceiptLineInput> lines) {
        for (GoodsReceiptLineInput line : lines) {
            BigDecimal lineTotal = line.qtyReceived().multiply(line.unitCost()).setScale(2, RoundingMode.HALF_UP);
            jdbcTemplate.update("""
                    INSERT INTO procurement.goods_receipt_line (
                        goods_receipt_id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost,
                        line_total, manufacture_date, expiry_date, note, created_at, updated_at
                    ) VALUES (
                        :goodsReceiptId, :purchaseOrderLineId, :ingredientId, :uomCode, :qtyReceived, :unitCost,
                        :lineTotal, :manufactureDate, :expiryDate, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "goodsReceiptId", goodsReceiptId,
                    "purchaseOrderLineId", line.purchaseOrderLineId(),
                    "ingredientId", line.ingredientId(),
                    "uomCode", line.uomCode(),
                    "qtyReceived", line.qtyReceived(),
                    "unitCost", line.unitCost(),
                    "lineTotal", lineTotal,
                    "manufactureDate", line.manufactureDate(),
                    "expiryDate", line.expiryDate(),
                    "note", line.note()
            ));
        }
    }

    private void insertInvoiceLines(Long supplierInvoiceId, List<SupplierInvoiceLineInput> lines) {
        int lineNumber = 1;
        for (SupplierInvoiceLineInput line : lines) {
            jdbcTemplate.update("""
                    INSERT INTO procurement.supplier_invoice_line (
                        supplier_invoice_id, line_number, line_type, goods_receipt_line_id, description, qty_invoiced,
                        unit_price, tax_percent, tax_amount, line_total, note, created_at, updated_at
                    ) VALUES (
                        :supplierInvoiceId, :lineNumber, :lineType, :goodsReceiptLineId, :description, :qtyInvoiced,
                        :unitPrice, :taxPercent, :taxAmount, :lineTotal, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "supplierInvoiceId", supplierInvoiceId,
                    "lineNumber", lineNumber++,
                    "lineType", line.lineType(),
                    "goodsReceiptLineId", line.goodsReceiptLineId(),
                    "description", line.description(),
                    "qtyInvoiced", line.qtyInvoiced(),
                    "unitPrice", line.unitPrice(),
                    "taxPercent", line.taxPercent() == null ? BigDecimal.ZERO : line.taxPercent(),
                    "taxAmount", line.taxAmount() == null ? BigDecimal.ZERO : line.taxAmount(),
                    "lineTotal", line.lineTotal(),
                    "note", line.note()
            ));
        }
    }

    private PurchaseOrderTotals calculatePurchaseOrderTotals(List<PurchaseOrderLineInput> lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        for (PurchaseOrderLineInput line : lines) {
            BigDecimal unitPrice = line.expectedUnitPrice() == null ? BigDecimal.ZERO : line.expectedUnitPrice();
            BigDecimal lineSubtotal = unitPrice.multiply(line.qtyOrdered()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = line.taxPercent() == null
                    ? BigDecimal.ZERO
                    : lineSubtotal.multiply(line.taxPercent().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTax);
        }
        return new PurchaseOrderTotals(subtotal, taxAmount, subtotal.add(taxAmount));
    }

    private BigDecimal calculateGoodsReceiptTotal(List<GoodsReceiptLineInput> lines) {
        return lines.stream()
                .map(line -> line.qtyReceived().multiply(line.unitCost()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void updatePurchaseOrderReceiptProgress(Long purchaseOrderId, Long goodsReceiptId) {
        List<GoodsReceiptLineResponse> receiptLines = mapGoodsReceipt(requireGoodsReceipt(goodsReceiptId)).lines();
        for (GoodsReceiptLineResponse receiptLine : receiptLines) {
            if (receiptLine.purchaseOrderLineId() == null) {
                continue;
            }
            jdbcTemplate.update("""
                    UPDATE procurement.purchase_order_line
                    SET qty_received = qty_received + :qtyReceived,
                        status = CASE
                            WHEN qty_received + :qtyReceived >= qty_ordered THEN 'COMPLETED'
                            ELSE 'PARTIALLY_RECEIVED'
                        END,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :purchaseOrderLineId
                    """, params(
                    "qtyReceived", receiptLine.qtyReceived(),
                    "purchaseOrderLineId", receiptLine.purchaseOrderLineId()
            ));
        }
        List<String> lineStatuses = jdbcTemplate.queryForList("""
                SELECT status
                FROM procurement.purchase_order_line
                WHERE purchase_order_id = :purchaseOrderId
                """, params("purchaseOrderId", purchaseOrderId), String.class);
        String headerStatus = lineStatuses.stream().allMatch("COMPLETED"::equals) ? "COMPLETED" : "PARTIALLY_RECEIVED";
        jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("status", headerStatus, "id", purchaseOrderId));
    }

    private void enqueueGoodsReceiptPostedEvent(GoodsReceiptRecord record, FernPrincipal principal) {
        List<GoodsReceiptPostedLine> lines = mapGoodsReceipt(record).lines().stream()
                .map(line -> new GoodsReceiptPostedLine(line.ingredientId(), line.qtyReceived(), line.unitCost(), line.id()))
                .toList();
        ProcurementGoodsReceiptPostedEvent event = new ProcurementGoodsReceiptPostedEvent(
                UUID.randomUUID().toString(),
                "procurement.goods_receipt.posted",
                Instant.now(clock),
                "procurement-service",
                currentCorrelationId(),
                UUID.randomUUID().toString(),
                record.id(),
                record.purchaseOrderId(),
                record.regionId(),
                record.outletId(),
                record.businessDate(),
                Instant.now(clock),
                principal.userId(),
                lines
        );
        enqueueOutbox("GOODS_RECEIPT", record.id().toString(), "procurement.goods_receipt.posted", record.outletId().toString(), event);
    }

    private void enqueueSupplierPaymentRecordedEvent(Long supplierPaymentId, FernPrincipal principal) {
        SupplierPaymentResponse payment = getSupplierPayment(supplierPaymentId);
        SupplierPaymentRecordedEvent event = new SupplierPaymentRecordedEvent(
                UUID.randomUUID().toString(),
                "procurement.supplier.payment.recorded",
                Instant.now(clock),
                "procurement-service",
                currentCorrelationId(),
                UUID.randomUUID().toString(),
                payment.id(),
                payment.supplierId(),
                payment.paymentTime(),
                payment.amount(),
                payment.currencyCode(),
                payment.invoiceAllocations().stream()
                        .map(allocation -> new SupplierPaymentAllocation(allocation.supplierInvoiceId(), allocation.allocatedAmount()))
                        .toList(),
                principal.userId()
        );
        enqueueOutbox("SUPPLIER_PAYMENT", payment.id().toString(), "procurement.supplier.payment.recorded", payment.supplierId().toString(), event);
    }

    private void enqueueOutbox(String aggregateType, String aggregateId, String eventType, String partitionKey, Object payload) {
        jdbcTemplate.update("""
                INSERT INTO procurement.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', CURRENT_TIMESTAMP
                )
                """, params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "partitionKey", partitionKey,
                "payload", toJson(payload)
        ));
    }

    private void ensureStatus(String actual, String expected, String message) {
        if (!expected.equals(actual)) {
            throw new ConflictException(message);
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private Long insertForId(NamedParameterJdbcTemplate template, String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    private MapSqlParameterSource params(Object... values) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value == null) {
                parameters.addValue((String) values[index], null, Types.NULL);
            } else if (value instanceof Instant instant) {
                parameters.addValue((String) values[index], OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else {
                parameters.addValue((String) values[index], value);
            }
        }
        return parameters;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private String currentCorrelationId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest().getHeader(CorrelationId.HEADER);
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record SupplierRecord(Long id, String status, Instant approvedAt) {
    }

    private record PurchaseOrderRecord(
            Long id,
            String poNumber,
            Long regionId,
            Long outletId,
            Long supplierId,
            LocalDate orderDate,
            LocalDate expectedDeliveryDate,
            String status,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String note,
            Instant approvedAt,
            Instant issuedAt
    ) {
    }

    private record GoodsReceiptRecord(
            Long id,
            String receiptNumber,
            Long purchaseOrderId,
            Long regionId,
            Long outletId,
            Long supplierId,
            Instant receiptTime,
            LocalDate businessDate,
            String status,
            BigDecimal totalAmount,
            String supplierLotNumber,
            String note,
            Instant receivedAt,
            Instant postedAt
    ) {
    }

    private record SupplierInvoiceRecord(
            Long id,
            Long supplierId,
            Long regionId,
            Long outletId,
            String currencyCode,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String status,
            String note,
            Instant approvedAt
    ) {
    }

    private record PurchaseOrderTotals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount) {
    }
}
