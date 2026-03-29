package com.fern.procurementservice.service;

import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierInvoiceRequest;
import com.fern.procurementservice.dto.ProcurementCommands.CreateSupplierPaymentRequest;
import com.fern.procurementservice.dto.ProcurementCommands.PaymentAllocationInput;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierInvoiceLineInput;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentAllocationResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PayablesService {
    private final ProcurementJdbcRepository procurementJdbcRepository;
    private final ProcurementEventPublisher procurementEventPublisher;
    private final ProcurementAuthorizer procurementAuthorizer;
    private final ProcurementOrgClient procurementOrgClient;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PayablesService(
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

    public SupplierInvoiceResponse createSupplierInvoice(FernPrincipal principal, CreateSupplierInvoiceRequest request) {
        procurementAuthorizer.requireRegionPermission(principal, request.regionId(), PermissionCodes.PROCUREMENT_INVOICE_REVIEW);
        ProcurementOrgClient.OutletRoute outlet = procurementOrgClient.requireOutlet(request.outletId());
        if (!outlet.regionId().equals(request.regionId())) {
            throw new ConflictException("Outlet route does not match requested region");
        }
        return transactionTemplate.execute(status -> createSupplierInvoiceTx(principal, request));
    }

    private SupplierInvoiceResponse createSupplierInvoiceTx(
            FernPrincipal principal,
            CreateSupplierInvoiceRequest request
    ) {
        SupplierRecord supplier = requireActiveApprovedSupplier(request.supplierId());
        BigDecimal subtotal = request.lines().stream()
                .map(line -> line.lineTotal().subtract(line.taxAmount() == null ? BigDecimal.ZERO : line.taxAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxAmount = request.lines().stream()
                .map(line -> line.taxAmount() == null ? BigDecimal.ZERO : line.taxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = request.lines().stream().map(SupplierInvoiceLineInput::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long id = insertForId(jdbcTemplate(), """
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
        int updated = jdbcTemplate().update("""
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
        if (invoiceAllocatedAmount(record.id()).compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Supplier invoices with recorded payments cannot be disputed");
        }
        int updated = jdbcTemplate().update("""
                UPDATE procurement.supplier_invoice
                SET status = 'DISPUTED',
                    disputed_by_user_id = :disputedByUserId,
                    disputed_at = :disputedAt,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status <> 'CANCELLED'
                """, params(
                "id", id,
                "disputedByUserId", principal.userId(),
                "disputedAt", Instant.now(clock)
        ));
        if (updated != 1) {
            throw new ConflictException("Cancelled invoices cannot be disputed");
        }
        return getSupplierInvoice(principal, id);
    }

    public SupplierPaymentResponse createSupplierPayment(
            FernPrincipal principal,
            String idempotencyKey,
            String correlationId,
            CreateSupplierPaymentRequest request
    ) {
        return transactionTemplate.execute(status -> createSupplierPaymentTx(principal, idempotencyKey, correlationId, request));
    }

    private SupplierPaymentResponse createSupplierPaymentTx(
            FernPrincipal principal,
            String idempotencyKey,
            String correlationId,
            CreateSupplierPaymentRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        procurementAuthorizer.requirePermission(principal, PermissionCodes.PROCUREMENT_PAYMENT_RECORD);
        Long existingPaymentId = jdbcTemplate().query("""
                SELECT id FROM procurement.supplier_payment WHERE idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
        if (existingPaymentId != null) {
            SupplierPaymentResponse existingPayment = getSupplierPayment(existingPaymentId);
            requireMatchingIdempotentSupplierPayment(existingPayment, request);
            return existingPayment;
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
        // Lock invoices in a stable order so concurrent allocation requests serialize on the same parent rows.
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
            Long lockedDuplicatePaymentId = jdbcTemplate().query("""
                    SELECT id FROM procurement.supplier_payment WHERE idempotency_key = :idempotencyKey
                    """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
            if (lockedDuplicatePaymentId != null) {
                SupplierPaymentResponse existingPayment = getSupplierPayment(lockedDuplicatePaymentId);
                requireMatchingIdempotentSupplierPayment(existingPayment, request);
                return existingPayment;
            }
            BigDecimal openAmount = invoice.totalAmount().subtract(invoiceAllocatedAmount(invoice.id()));
            if (requestedAllocationByInvoice.get(invoice.id()).compareTo(openAmount) > 0) {
                throw new ConflictException("Allocated amount exceeds the supplier invoice open amount");
            }
        }
        Long id = insertForId(jdbcTemplate(), """
                INSERT INTO procurement.supplier_payment (
                    payment_number, supplier_id, currency_code, payment_method, amount, payment_time, transaction_ref,
                    note, created_by_user_id, created_at, updated_at, idempotency_key
                ) VALUES (
                    :paymentNumber, :supplierId, :currencyCode, :paymentMethod, :amount, :paymentTime, :transactionRef,
                    :note, :createdByUserId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :idempotencyKey
                )
                """, params(
                "paymentNumber", nextReferenceNumber("SP"),
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
            jdbcTemplate().update("""
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
        procurementEventPublisher.enqueueSupplierPaymentRecordedEvent(id, principal, correlationId);
        return getSupplierPayment(id);
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private void requireMatchingIdempotentSupplierPayment(
            SupplierPaymentResponse existingPayment,
            CreateSupplierPaymentRequest request
    ) {
        if (!matchesExistingSupplierPayment(existingPayment, request)) {
            throw new ConflictException("Idempotency-Key cannot be reused with a different supplier payment request");
        }
    }

    private boolean matchesExistingSupplierPayment(
            SupplierPaymentResponse existingPayment,
            CreateSupplierPaymentRequest request
    ) {
        return Objects.equals(existingPayment.supplierId(), request.supplierId())
                && Objects.equals(existingPayment.currencyCode(), request.currencyCode())
                && Objects.equals(existingPayment.paymentMethod(), request.paymentMethod())
                && bigDecimalEquals(existingPayment.amount(), request.amount())
                && Objects.equals(existingPayment.paymentTime(), request.paymentTime())
                && Objects.equals(existingPayment.transactionRef(), request.transactionRef())
                && Objects.equals(existingPayment.note(), request.note())
                && normalizeSupplierPaymentAllocations(existingPayment.invoiceAllocations())
                        .equals(normalizeRequestedPaymentAllocations(request.invoiceAllocations()));
    }

    private List<NormalizedSupplierPaymentAllocation> normalizeSupplierPaymentAllocations(
            List<SupplierPaymentAllocationResponse> allocations
    ) {
        return allocations.stream()
                .map(allocation -> new NormalizedSupplierPaymentAllocation(
                        allocation.supplierInvoiceId(),
                        allocation.allocatedAmount(),
                        allocation.note()
                ))
                .sorted()
                .toList();
    }

    private List<NormalizedSupplierPaymentAllocation> normalizeRequestedPaymentAllocations(
            List<PaymentAllocationInput> allocations
    ) {
        return allocations.stream()
                .map(allocation -> new NormalizedSupplierPaymentAllocation(
                        allocation.supplierInvoiceId(),
                        allocation.allocatedAmount(),
                        allocation.note()
                ))
                .sorted()
                .toList();
    }

    private boolean bigDecimalEquals(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        return procurementJdbcRepository.jdbcTemplate();
    }

    private String nextReferenceNumber(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private MapSqlParameterSource params(Object... values) {
        return procurementJdbcRepository.params(values);
    }

    private Long insertForId(NamedParameterJdbcTemplate template, String sql, MapSqlParameterSource parameters) {
        return procurementJdbcRepository.insertForId(template, sql, parameters);
    }

    private SupplierRecord requireActiveApprovedSupplier(Long id) {
        return procurementJdbcRepository.requireActiveApprovedSupplier(id);
    }

    private void insertInvoiceLines(Long supplierInvoiceId, List<SupplierInvoiceLineInput> lines) {
        procurementJdbcRepository.insertInvoiceLines(supplierInvoiceId, lines);
    }

    private SupplierInvoiceRecord requireSupplierInvoice(Long id) {
        return procurementJdbcRepository.requireSupplierInvoice(id);
    }

    private SupplierInvoiceResponse mapSupplierInvoice(SupplierInvoiceRecord record) {
        return procurementJdbcRepository.mapSupplierInvoice(record);
    }

    private SupplierInvoiceRecord requireSupplierInvoiceForUpdate(Long id) {
        return procurementJdbcRepository.requireSupplierInvoiceForUpdate(id);
    }

    private SupplierPaymentResponse getSupplierPayment(Long id) {
        return procurementJdbcRepository.getSupplierPayment(id);
    }

    private BigDecimal invoiceAllocatedAmount(Long supplierInvoiceId) {
        return procurementJdbcRepository.invoiceAllocatedAmount(supplierInvoiceId);
    }

    private record NormalizedSupplierPaymentAllocation(
            Long supplierInvoiceId,
            BigDecimal allocatedAmount,
            String note
    ) implements Comparable<NormalizedSupplierPaymentAllocation> {
        @Override
        public int compareTo(NormalizedSupplierPaymentAllocation other) {
            int byInvoiceId = supplierInvoiceId.compareTo(other.supplierInvoiceId);
            if (byInvoiceId != 0) {
                return byInvoiceId;
            }
            int byAmount = allocatedAmount.compareTo(other.allocatedAmount);
            if (byAmount != 0) {
                return byAmount;
            }
            if (note == null && other.note == null) {
                return 0;
            }
            if (note == null) {
                return -1;
            }
            if (other.note == null) {
                return 1;
            }
            return note.compareTo(other.note);
        }
    }
}
