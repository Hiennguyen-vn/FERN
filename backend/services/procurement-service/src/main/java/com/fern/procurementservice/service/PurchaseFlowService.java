package com.fern.procurementservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.procurementservice.dto.ProcurementCommands.CreateGoodsReceiptRequest;
import com.fern.procurementservice.dto.ProcurementCommands.CreatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementCommands.GoodsReceiptLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.PurchaseOrderLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.UpdatePurchaseOrderRequest;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptResponse;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PurchaseFlowService {
    private final ProcurementJdbcRepository procurementJdbcRepository;
    private final ProcurementEventPublisher procurementEventPublisher;
    private final ProcurementAuthorizer procurementAuthorizer;
    private final ProcurementOrgClient procurementOrgClient;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PurchaseFlowService(
            ProcurementJdbcRepository procurementJdbcRepository,
            ProcurementEventPublisher procurementEventPublisher,
            ProcurementAuthorizer procurementAuthorizer,
            ProcurementOrgClient procurementOrgClient,
            Clock clock,
            TransactionTemplate transactionTemplate
    ) {
        this.procurementJdbcRepository = procurementJdbcRepository;
        this.procurementEventPublisher = procurementEventPublisher;
        this.procurementAuthorizer = procurementAuthorizer;
        this.procurementOrgClient = procurementOrgClient;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public PurchaseOrderResponse createPurchaseOrder(FernPrincipal principal, CreatePurchaseOrderRequest request) {
        procurementAuthorizer.requireOutletPermission(principal, request.outletId(), PermissionCodes.PROCUREMENT_PO_CREATE);
        ProcurementOrgClient.OutletRoute outlet = procurementOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new BadRequestException("Region does not match the outlet route");
        }
        ensureOutletOperational(outlet, request.orderDate());
        return transactionTemplate.execute(status -> createPurchaseOrderTx(principal, request, outlet));
    }

    private PurchaseOrderResponse createPurchaseOrderTx(
            FernPrincipal principal,
            CreatePurchaseOrderRequest request,
            ProcurementOrgClient.OutletRoute outlet
    ) {
        SupplierRecord supplier = requireActiveApprovedSupplier(request.supplierId());
        PurchaseOrderTotals totals = calculatePurchaseOrderTotals(request.lines());
        Long id = insertForId(jdbcTemplate(), """
                INSERT INTO procurement.purchase_order (
                    po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status,
                    subtotal_amount, tax_amount, total_amount, note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :poNumber, :regionId, :outletId, :supplierId, :orderDate, :expectedDeliveryDate, 'DRAFT',
                    :subtotalAmount, :taxAmount, :totalAmount, :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "poNumber", nextReferenceNumber("PO"),
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
        requireActiveApprovedSupplier(record.supplierId());
        PurchaseOrderTotals totals = calculatePurchaseOrderTotals(request.lines());
        int updated = jdbcTemplate().update("""
                UPDATE procurement.purchase_order
                SET expected_delivery_date = :expectedDeliveryDate,
                    subtotal_amount = :subtotalAmount,
                    tax_amount = :taxAmount,
                    total_amount = :totalAmount,
                    note = :note,
                    updated_by_user_id = :updatedByUserId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = 'DRAFT'
                """, params(
                "expectedDeliveryDate", request.expectedDeliveryDate(),
                "subtotalAmount", totals.subtotal(),
                "taxAmount", totals.taxAmount(),
                "totalAmount", totals.totalAmount(),
                "note", request.note(),
                "updatedByUserId", principal.userId(),
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
        int updated = jdbcTemplate().update("""
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
        int updated = jdbcTemplate().update("""
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
        int updated = jdbcTemplate().update("""
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
        int updated = jdbcTemplate().update("""
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
        if (!List.of("ORDERED", "PARTIALLY_RECEIVED").contains(purchaseOrder.status())) {
            throw new ConflictException("Goods receipts can only be created from ordered or partially received purchase orders");
        }
        ensureOutletOperational(procurementOrgClient.requireOutlet(purchaseOrder.outletId()), request.businessDate());
        procurementJdbcRepository.validateGoodsReceiptLines(purchaseOrder.id(), request.lines());
        BigDecimal totalAmount = calculateGoodsReceiptTotal(request.lines());
        Long id = insertForId(jdbcTemplate(), """
                INSERT INTO procurement.goods_receipt (
                    receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date, status,
                    total_amount, supplier_lot_number, note, created_by_user_id, created_at, updated_at
                ) VALUES (
                    :receiptNumber, :purchaseOrderId, :regionId, :outletId, :supplierId, :receiptTime, :businessDate, 'DRAFT',
                    :totalAmount, :supplierLotNumber, :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "receiptNumber", nextReferenceNumber("GR"),
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
    public List<PurchaseOrderResponse> listPurchaseOrders(FernPrincipal principal, Long outletId, Long supplierId, String status, int limit) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_PO_READ);
        var records = procurementJdbcRepository.listPurchaseOrders(outletId, supplierId, status, limit).stream()
                .filter(record -> com.fern.platform.common.ScopeAccess.allowsRoute(principal, record.regionId(), record.outletId()))
                .toList();
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> ids = records.stream().map(r -> r.id()).toList();
        Map<Long, List<com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderLineResponse>> linesByPoId =
                procurementJdbcRepository.batchLoadPurchaseOrderLines(ids);
        return records.stream()
                .map(r -> procurementJdbcRepository.mapPurchaseOrderWithLines(r, linesByPoId.getOrDefault(r.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> listGoodsReceipts(FernPrincipal principal, Long purchaseOrderId, Long outletId, String status, int limit) {
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_GR_READ);
        var records = procurementJdbcRepository.listGoodsReceipts(purchaseOrderId, outletId, status, limit).stream()
                .filter(record -> com.fern.platform.common.ScopeAccess.allowsRoute(principal, record.regionId(), record.outletId()))
                .toList();
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> ids = records.stream().map(r -> r.id()).toList();
        Map<Long, List<com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptLineResponse>> linesByGrId =
                procurementJdbcRepository.batchLoadGoodsReceiptLines(ids);
        return records.stream()
                .map(r -> procurementJdbcRepository.mapGoodsReceiptWithLines(r, linesByGrId.getOrDefault(r.id(), List.of())))
                .toList();
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
        int updated = jdbcTemplate().update("""
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
    public GoodsReceiptResponse postGoodsReceipt(FernPrincipal principal, Long id, String idempotencyKey, String correlationId) {
        requireIdempotencyKey(idempotencyKey);
        GoodsReceiptRecord record = requireGoodsReceiptForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_GR_POST);
        Long duplicateId = jdbcTemplate().query("""
                SELECT id FROM procurement.goods_receipt WHERE posted_idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
        if (duplicateId != null) {
            if (!duplicateId.equals(id)) {
                throw new ConflictException("Idempotency-Key cannot be reused with a different goods receipt post request");
            }
            return getGoodsReceipt(principal, duplicateId);
        }
        ensureStatus(record.status(), "RECEIVED", "Only received goods receipts can be posted");
        procurementJdbcRepository.validateGoodsReceiptPosting(record.purchaseOrderId(), id);
        int updated = jdbcTemplate().update("""
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
        procurementEventPublisher.enqueueGoodsReceiptPostedEvent(record, principal, correlationId);
        return getGoodsReceipt(principal, id);
    }

    @Transactional
    public GoodsReceiptResponse cancelGoodsReceipt(FernPrincipal principal, Long id) {
        GoodsReceiptRecord record = requireGoodsReceiptForUpdate(id);
        procurementAuthorizer.requireOutletPermission(principal, record.outletId(), PermissionCodes.PROCUREMENT_GR_CANCEL);
        if (!List.of("DRAFT", "RECEIVED").contains(record.status())) {
            throw new ConflictException("Only draft or received goods receipts can be cancelled");
        }
        int updated = jdbcTemplate().update("""
                UPDATE procurement.goods_receipt
                SET status = 'CANCELLED',
                    cancelled_by_user_id = :cancelledByUserId,
                    cancelled_at = :cancelledAt,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :status
                """, params(
                "id", id,
                "status", record.status(),
                "cancelledByUserId", principal.userId(),
                "cancelledAt", Instant.now(clock)
        ));
        if (updated != 1) {
            throw new ConflictException("Only draft or received goods receipts can be cancelled");
        }
        return getGoodsReceipt(principal, id);
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

    private NamedParameterJdbcTemplate jdbcTemplate() {
        return procurementJdbcRepository.jdbcTemplate();
    }

    private String nextReferenceNumber(String prefix) {
        Long nextVal = jdbcTemplate().queryForObject(
                "SELECT nextval('procurement.reference_number_seq')",
                new MapSqlParameterSource(),
                Long.class
        );
        String datePart = java.time.LocalDate.now(clock).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMM")
        );
        return prefix + "-" + datePart + "-" + String.format("%06d", nextVal);
    }

    private void ensureOutletOperational(ProcurementOrgClient.OutletRoute outlet, java.time.LocalDate businessDate) {
        if (!outlet.isActive() || outlet.isClosedOn(businessDate)) {
            throw new ConflictException("Outlet is inactive or closed for procurement workflows");
        }
    }

    private MapSqlParameterSource params(Object... values) {
        return procurementJdbcRepository.params(values);
    }

    private Long insertForId(NamedParameterJdbcTemplate template, String sql, MapSqlParameterSource parameters) {
        return procurementJdbcRepository.insertForId(template, sql, parameters);
    }

    private String toJson(Object payload) {
        return procurementJdbcRepository.toJson(payload);
    }

    private SupplierRecord requireActiveApprovedSupplier(Long id) {
        return procurementJdbcRepository.requireActiveApprovedSupplier(id);
    }

    private PurchaseOrderRecord requirePurchaseOrder(Long id) {
        return procurementJdbcRepository.requirePurchaseOrder(id);
    }

    private PurchaseOrderRecord requirePurchaseOrderForUpdate(Long id) {
        return procurementJdbcRepository.requirePurchaseOrderForUpdate(id);
    }

    private PurchaseOrderResponse mapPurchaseOrder(PurchaseOrderRecord record) {
        return procurementJdbcRepository.mapPurchaseOrder(record);
    }

    private void replacePurchaseOrderLines(Long purchaseOrderId, List<PurchaseOrderLineInput> lines) {
        procurementJdbcRepository.replacePurchaseOrderLines(purchaseOrderId, lines);
    }

    private GoodsReceiptRecord requireGoodsReceipt(Long id) {
        return procurementJdbcRepository.requireGoodsReceipt(id);
    }

    private GoodsReceiptRecord requireGoodsReceiptForUpdate(Long id) {
        return procurementJdbcRepository.requireGoodsReceiptForUpdate(id);
    }

    private GoodsReceiptResponse mapGoodsReceipt(GoodsReceiptRecord record) {
        return procurementJdbcRepository.mapGoodsReceipt(record);
    }

    private void insertGoodsReceiptLines(Long goodsReceiptId, List<GoodsReceiptLineInput> lines) {
        procurementJdbcRepository.insertGoodsReceiptLines(goodsReceiptId, lines);
    }

    private void updatePurchaseOrderReceiptProgress(Long purchaseOrderId, Long goodsReceiptId) {
        procurementJdbcRepository.updatePurchaseOrderReceiptProgress(purchaseOrderId, goodsReceiptId);
    }
}
