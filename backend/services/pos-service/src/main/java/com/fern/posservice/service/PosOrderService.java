package com.fern.posservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.OperationalShardAccess;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardResolver;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.security.RedisSchedulerLock;
import com.fern.posservice.dto.PosCommands.AddPaymentRequest;
import com.fern.posservice.dto.PosCommands.CreateSaleOrderRequest;
import com.fern.posservice.dto.PosCommands.UpdateSaleOrderRequest;
import com.fern.posservice.dto.PosResponses.CustomerSummaryResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderLineResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import com.fern.posservice.dto.PosResponses.SalePaymentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PosOrderService {
    private static final Logger log = LoggerFactory.getLogger(PosOrderService.class);

    /** Service-level principal used for automated recovery tasks that have no user context. */
    private static final FernPrincipal SYSTEM_PRINCIPAL = new FernPrincipal(
            0L, "pos-service-system",
            Set.of(), Set.of(PermissionCodes.INVENTORY_INTERNAL_RELEASE),
            new com.fern.platform.common.ScopeRoots(true, List.of(), List.of()),
            0L, 0L, "pos-system-recovery",
            com.fern.platform.common.FernPrincipalType.SERVICE
    );

    private final PosAuthorizer posAuthorizer;
    private final PosStore store;
    private final PosOrgClient posOrgClient;
    private final PosPricingService pricingService;
    private final PosInventoryClient inventoryClient;
    private final PosReferenceCodeGenerator codeGenerator;
    private final PosCustomerService customerService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final PosAuditService posAuditService;
    private final Counter paymentFailureCounter;
    private final Counter completionRecoveryCounter;
    private final OperationalShardRegistry operationalShardRegistry;
    private final ShardResolver shardResolver;
    private final Duration completionRecoveryAge;
    private final int completionRecoveryBatchSize;
    private final RedisSchedulerLock schedulerLock;
    private final PosDineInService dineInService;

    public PosOrderService(
            PosAuthorizer posAuthorizer,
            PosStore store,
            PosOrgClient posOrgClient,
            PosPricingService pricingService,
            PosInventoryClient inventoryClient,
            PosReferenceCodeGenerator codeGenerator,
            PosCustomerService customerService,
            ObjectMapper objectMapper,
            Clock clock,
            OperationalAlertPublisher operationalAlertPublisher,
            PosAuditService posAuditService,
            MeterRegistry meterRegistry,
            OperationalShardRegistry operationalShardRegistry,
            ShardResolver shardResolver,
            @Value("${fern.pos.completion-recovery.age:PT2M}") Duration completionRecoveryAge,
            @Value("${fern.pos.completion-recovery.batch-size:50}") int completionRecoveryBatchSize,
            @org.springframework.lang.Nullable RedisSchedulerLock schedulerLock,
            PosDineInService dineInService
    ) {
        this.posAuthorizer = posAuthorizer;
        this.store = store;
        this.posOrgClient = posOrgClient;
        this.pricingService = pricingService;
        this.inventoryClient = inventoryClient;
        this.codeGenerator = codeGenerator;
        this.customerService = customerService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.posAuditService = posAuditService;
        this.paymentFailureCounter = Counter.builder("fern_payment_failures_total").register(meterRegistry);
        this.completionRecoveryCounter = Counter.builder("fern_pos_completion_recoveries_total").register(meterRegistry);
        this.operationalShardRegistry = operationalShardRegistry;
        this.shardResolver = shardResolver;
        this.completionRecoveryAge = completionRecoveryAge;
        this.completionRecoveryBatchSize = completionRecoveryBatchSize;
        this.schedulerLock = schedulerLock;
        this.dineInService = dineInService;
    }

    public SaleOrderResponse createOrder(FernPrincipal principal, CreateSaleOrderRequest request) {
        // Resolve session from root template to get regionId/outletId for shard selection
        SessionRecord session = store.requireSession(rootJdbcTemplate(), request.posSessionId());
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(session.regionId(), session.outletId());
        TransactionTemplate transactionTemplate = transactionTemplate(session.regionId(), session.outletId());
        posAuthorizer.requireRoutePermission(principal, session.regionId(), session.outletId(), PermissionCodes.POS_ORDER_CREATE);
        ensureSessionOpen(session);
        ensureOutletOperational(session.outletId(), session.businessDate());
        customerService.requireActiveCustomer(request.customerId());
        PricingSnapshot pricingSnapshot = pricingService.resolvePricingSnapshot(
                principal,
                session.outletId(),
                session.regionId(),
                session.businessDate(),
                request.lines(),
                request.promotionCode()
        );
        Long id = Objects.requireNonNull(transactionTemplate.execute(status -> {
            SessionRecord currentSession = store.requireSessionForUpdate(jdbcTemplate, request.posSessionId());
            ensureSessionOpen(currentSession);
            ensureOutletOperational(currentSession.outletId(), currentSession.businessDate());
            Long orderId = PosSql.insertForId(jdbcTemplate, """
                    INSERT INTO pos.sale_order (
                        order_number, region_id, outlet_id, pos_session_id, currency_code, order_type, status,
                        payment_status, subtotal, discount_amount, tax_amount, total_amount, promotion_code, customer_id, table_id, note, created_at, updated_at
                    ) VALUES (
                        :orderNumber, :regionId, :outletId, :sessionId, :currencyCode, :orderType, :status,
                        :paymentStatus, :subtotal, :discountAmount, :taxAmount, :totalAmount, :promotionCode, :customerId, :tableId, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, PosSql.params(
                    "orderNumber", codeGenerator.nextOrderNumber(),
                    "regionId", currentSession.regionId(),
                    "outletId", currentSession.outletId(),
                    "sessionId", currentSession.id(),
                    "currencyCode", currentSession.currencyCode(),
                    "orderType", request.orderType(),
                    "status", SaleOrderStatus.OPEN.name(),
                    "paymentStatus", SaleOrderPaymentStatus.UNPAID.name(),
                    "subtotal", pricingSnapshot.subtotal(),
                    "discountAmount", pricingSnapshot.discountAmount(),
                    "taxAmount", pricingSnapshot.taxAmount(),
                    "totalAmount", pricingSnapshot.totalAmount(),
                    "promotionCode", pricingSnapshot.promotionCode(),
                    "customerId", request.customerId(),
                    "tableId", request.tableId(),
                    "note", request.note()
            ));
            store.replaceOrderLines(jdbcTemplate, orderId, pricingSnapshot.lines());
            // Assign table to order if dine-in
            if (request.tableId() != null) {
                dineInService.assignTableToOrder(request.tableId(), orderId, currentSession.regionId(), currentSession.outletId());
            }
            return orderId;
        }));
        return getOrder(principal, id);
    }

    public SaleOrderResponse getOrder(FernPrincipal principal, Long id) {
        // Use root template to resolve regionId/outletId, then switch to shard-specific template for line/payment queries
        OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_READ);
        CustomerSummaryResponse customerSummary = store.resolveCustomerSummary(rootJdbcTemplate(), order.customerId());
        return store.mapOrder(jdbcTemplate, order, customerSummary);
    }

    public java.util.Map<String, Object> getSaleOrderSnapshot(FernPrincipal principal, Long id) {
        OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_READ);
        java.util.Map<String, Object> snapshot = store.getSaleSnapshot(jdbcTemplate, id);
        if (snapshot == null) {
            throw new com.fern.platform.common.ResourceNotFoundException("Sale snapshot not available for this order");
        }
        return snapshot;
    }

    public List<SaleOrderResponse> listOrdersBySession(FernPrincipal principal, Long posSessionId, int limit) {
        SessionRecord session = store.requireSession(rootJdbcTemplate(), posSessionId);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(session.regionId(), session.outletId());
        posAuthorizer.requireRoutePermission(principal, session.regionId(), session.outletId(), PermissionCodes.POS_ORDER_READ);
        return store.listOrdersBySession(jdbcTemplate, posSessionId, limit).stream()
                .map(order -> {
                    CustomerSummaryResponse cs = store.resolveCustomerSummary(rootJdbcTemplate(), order.customerId());
                    return store.mapOrder(order, store.queryOrderLines(jdbcTemplate, order.id()),
                            store.queryPayments(jdbcTemplate, order.id()), cs);
                })
                .toList();
    }

    public SaleOrderResponse updateOrder(FernPrincipal principal, Long id, UpdateSaleOrderRequest request) {
        OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
        TransactionTemplate transactionTemplate = transactionTemplate(order.regionId(), order.outletId());
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_UPDATE);
        ensureOrderOpen(order);
        ensureNoSuccessfulPayments(jdbcTemplate, id);
        customerService.requireActiveCustomer(request.customerId());
        SessionRecord session = store.requireSession(jdbcTemplate, order.posSessionId());
        PricingSnapshot pricingSnapshot = pricingService.resolvePricingSnapshot(
                principal,
                order.outletId(),
                order.regionId(),
                session.businessDate(),
                request.lines(),
                request.promotionCode()
        );
        transactionTemplate.executeWithoutResult(status -> {
            OrderRecord currentOrder = store.requireOrderForUpdate(jdbcTemplate, id);
            ensureOrderOpen(currentOrder);
            ensureNoSuccessfulPayments(jdbcTemplate, id);
            jdbcTemplate.update("""
                    UPDATE pos.sale_order
                    SET order_type = COALESCE(:orderType, order_type),
                        subtotal = :subtotal,
                        discount_amount = :discountAmount,
                        tax_amount = :taxAmount,
                        total_amount = :totalAmount,
                        promotion_code = :promotionCode,
                        customer_id = COALESCE(:customerId, customer_id),
                        note = COALESCE(:note, note),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, PosSql.params(
                    "orderType", request.orderType(),
                    "subtotal", pricingSnapshot.subtotal(),
                    "discountAmount", pricingSnapshot.discountAmount(),
                    "taxAmount", pricingSnapshot.taxAmount(),
                    "totalAmount", pricingSnapshot.totalAmount(),
                    "promotionCode", pricingSnapshot.promotionCode(),
                    "customerId", request.customerId(),
                    "note", request.note(),
                    "id", id
            ));
            store.replaceOrderLines(jdbcTemplate, id, pricingSnapshot.lines());
            store.refreshPaymentStatus(jdbcTemplate, id);
        });
        return getOrder(principal, id);
    }

    public SaleOrderResponse addPayment(
            FernPrincipal principal,
            Long id,
            String idempotencyKey,
            String correlationId,
            AddPaymentRequest request
    ) {
        requireIdempotencyKey(idempotencyKey);
        OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
        TransactionTemplate transactionTemplate = transactionTemplate(order.regionId(), order.outletId());
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_UPDATE);
        ensureOrderOpen(order);
        transactionTemplate.executeWithoutResult(status -> {
            OrderRecord currentOrder = store.requireOrderForUpdate(jdbcTemplate, id);
            ensureOrderOpen(currentOrder);
            ExistingPayment existingPayment = jdbcTemplate.query("""
                    SELECT id, sale_order_id, payment_method, amount, status, payment_time, transaction_ref, note
                    FROM pos.sale_payment
                    WHERE idempotency_key = :idempotencyKey
                    """, PosSql.params("idempotencyKey", idempotencyKey), rs -> rs.next()
                    ? new ExistingPayment(
                            rs.getLong("id"),
                            rs.getLong("sale_order_id"),
                            rs.getString("payment_method"),
                            rs.getBigDecimal("amount"),
                            rs.getString("status"),
                            PosSql.instant(rs, "payment_time"),
                            rs.getString("transaction_ref"),
                            rs.getString("note")
                    )
                    : null);
            if (existingPayment != null) {
                requireMatchingIdempotentPayment(existingPayment, id, request);
                return;
            }
            SalePaymentStatus paymentStatus = resolvePaymentStatus(request.status());
            if (paymentStatus == SalePaymentStatus.SUCCESS) {
                BigDecimal currentSuccessAmount = store.successfulPaymentTotal(jdbcTemplate, id);
                if (currentSuccessAmount.add(request.amount()).compareTo(currentOrder.totalAmount()) > 0) {
                    throw new ConflictException("Successful payments cannot exceed order total");
                }
            }
            PosSql.insertForId(jdbcTemplate, """
                    INSERT INTO pos.sale_payment (
                        sale_order_id, pos_session_id, payment_method, amount, status, payment_time, transaction_ref, note, created_at, idempotency_key
                    ) VALUES (
                        :saleOrderId, :sessionId, :paymentMethod, :amount, :status, :paymentTime, :transactionRef, :note, CURRENT_TIMESTAMP, :idempotencyKey
                    )
                    """, PosSql.params(
                    "saleOrderId", id,
                    "sessionId", currentOrder.posSessionId(),
                    "paymentMethod", request.paymentMethod(),
                    "amount", request.amount(),
                    "status", paymentStatus.name(),
                    "paymentTime", request.paymentTime() == null ? clock.instant() : request.paymentTime(),
                    "transactionRef", request.transactionRef(),
                    "note", request.note(),
                    "idempotencyKey", idempotencyKey
            ));
            if (paymentStatus == SalePaymentStatus.FAILED) {
                paymentFailureCounter.increment();
                operationalAlertPublisher.publish(
                        "PAYMENT_FAILED",
                        "MEDIUM",
                        "Sale payment failed for order " + id,
                        correlationId,
                        currentOrder.regionId(),
                        currentOrder.outletId(),
                        "SALE_ORDER",
                        id.toString(),
                        Map.of(
                                "paymentMethod", request.paymentMethod(),
                                "amount", request.amount(),
                                "transactionRef", request.transactionRef()
                        )
                );
            }
            store.refreshPaymentStatus(jdbcTemplate, id);
        });
        return getOrder(principal, id);
    }

    private SalePaymentStatus resolvePaymentStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException("Payment status is required");
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        try {
            SalePaymentStatus paymentStatus = SalePaymentStatus.valueOf(normalized);
            if (!EnumSet.of(SalePaymentStatus.SUCCESS, SalePaymentStatus.FAILED, SalePaymentStatus.CANCELLED).contains(paymentStatus)) {
                throw new IllegalArgumentException("Unsupported payment status");
            }
            return paymentStatus;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported payment status");
        }
    }

    private void requireMatchingIdempotentPayment(
            ExistingPayment existingPayment,
            Long saleOrderId,
            AddPaymentRequest request
    ) {
        if (!matchesExistingPayment(existingPayment, saleOrderId, request)) {
            throw new ConflictException("Idempotency-Key cannot be reused with a different sale payment request");
        }
    }

    private boolean matchesExistingPayment(
            ExistingPayment existingPayment,
            Long saleOrderId,
            AddPaymentRequest request
    ) {
        return Objects.equals(existingPayment.saleOrderId(), saleOrderId)
                && Objects.equals(existingPayment.paymentMethod(), request.paymentMethod())
                && bigDecimalEquals(existingPayment.amount(), request.amount())
                && Objects.equals(existingPayment.status(), resolvePaymentStatus(request.status()).name())
                && matchesExistingPaymentTime(existingPayment.paymentTime(), request.paymentTime())
                && Objects.equals(existingPayment.transactionRef(), request.transactionRef())
                && Objects.equals(existingPayment.note(), request.note());
    }

    private boolean matchesExistingPaymentTime(Instant existingPaymentTime, Instant requestedPaymentTime) {
        if (requestedPaymentTime == null) {
            return true;
        }
        return Objects.equals(existingPaymentTime, requestedPaymentTime);
    }

    private boolean bigDecimalEquals(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    public SaleOrderResponse completeOrder(FernPrincipal principal, Long id, String correlationId) {
        OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
        TransactionTemplate transactionTemplate = transactionTemplate(order.regionId(), order.outletId());
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_COMPLETE);
        if (isCompletionReplay(order)) {
            return getOrder(principal, id);
        }
        ensureOrderOpenForCompletion(order);
        if (store.successfulPaymentTotal(jdbcTemplate, id).compareTo(order.totalAmount()) < 0) {
            throw new ConflictException("Order cannot be completed until payment covers the full total");
        }
        CompletionPreflight preflight = Objects.requireNonNull(
                transactionTemplate.execute(status -> prepareCompletionPreflight(principal, id, jdbcTemplate))
        );
        SaleReservationResponse reservation = null;
        try {
            reservation = inventoryClient.reserveInventory(
                    principal,
                    preflight.order().outletId(),
                    preflight.session().businessDate(),
                    id,
                    preflight.usageItems()
            );
            List<SalePaymentResponse> payments = store.queryPayments(jdbcTemplate, id);
            Map<String, Object> saleSnapshot = buildSaleSnapshot(
                    preflight.order(),
                    preflight.pricingSnapshot(),
                    payments,
                    preflight.recipeSnapshots(),
                    reservation.reservationId()
            );
            Instant completedAt = clock.instant();
            CompletionPreflight finalizedPreflight = preflight;
            FernPrincipal completedBy = principal;
            List<SalePaymentResponse> finalizedPayments = payments;
            Map<String, Object> finalizedSaleSnapshot = saleSnapshot;
            Instant finalizedCompletedAt = completedAt;
            Long finalizedOrderId = id;
            SaleReservationResponse completedReservation = reservation;
            NamedParameterJdbcTemplate finalizedJdbcTemplate = jdbcTemplate;
            transactionTemplate.executeWithoutResult(status -> {
                OrderRecord currentOrder = store.requireOrderForUpdate(finalizedJdbcTemplate, finalizedOrderId);
                if (isCompletionReplay(currentOrder)) {
                    return;
                }
                ensureOrderCompleting(currentOrder);
                if (store.successfulPaymentTotal(finalizedJdbcTemplate, finalizedOrderId).compareTo(currentOrder.totalAmount()) < 0) {
                    throw new ConflictException("Order cannot be completed until payment covers the full total");
                }
                SessionRecord currentSession = store.requireSession(finalizedJdbcTemplate, currentOrder.posSessionId());
                jdbcTemplate.update("""
                        INSERT INTO pos.sale_snapshot (sale_order_id, order_snapshot, created_at)
                        VALUES (:saleOrderId, CAST(:orderSnapshot AS jsonb), CURRENT_TIMESTAMP)
                        ON CONFLICT (sale_order_id) DO UPDATE
                        SET order_snapshot = EXCLUDED.order_snapshot
                        """, PosSql.params("saleOrderId", finalizedOrderId, "orderSnapshot", toJson(finalizedSaleSnapshot)));
                jdbcTemplate.update("""
                        UPDATE pos.sale_order
                        SET status = :status,
                            payment_status = :paymentStatus,
                            subtotal = :subtotal,
                            tax_amount = :taxAmount,
                            total_amount = :totalAmount,
                            completed_at = :completedAt,
                            completed_by_user_id = :completedByUserId,
                            reservation_id = :reservationId,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """, PosSql.params(
                        "status", SaleOrderStatus.COMPLETED.name(),
                        "paymentStatus", SaleOrderPaymentStatus.PAID.name(),
                        "subtotal", finalizedPreflight.pricingSnapshot().subtotal(),
                        "taxAmount", finalizedPreflight.pricingSnapshot().taxAmount(),
                        "totalAmount", finalizedPreflight.pricingSnapshot().totalAmount(),
                        "completedAt", finalizedCompletedAt,
                        "completedByUserId", completedBy.userId(),
                        "reservationId", completedReservation.reservationId(),
                        "id", finalizedOrderId
                ));
                enqueueSaleCompletedEvent(
                        jdbcTemplate,
                        currentOrder,
                        currentSession,
                        finalizedPreflight.pricingSnapshot(),
                        finalizedPayments,
                        finalizedSaleSnapshot,
                        finalizedPreflight.usageItems(),
                        completedReservation,
                        completedBy,
                        finalizedCompletedAt,
                        correlationId
                );
                // BUG-004: Accrue loyalty points inside the transaction so it's atomic with completion
                customerService.accrueVisit(currentOrder.customerId(), finalizedOrderId, currentOrder.outletId(), currentOrder.totalAmount());
            });
        } catch (RuntimeException exception) {
            if (reservation != null) {
                releaseReservationAfterFailure(principal, reservation, exception);
            }
            revertCompletingOrder(order.regionId(), order.outletId(), id);
            throw exception;
        }
        // Release dining table after successful completion
        dineInService.releaseTable(order.tableId(), order.regionId(), order.outletId());
        return getOrder(principal, id);
    }

    @Scheduled(fixedDelayString = "${fern.pos.completion-recovery.delay-ms:30000}")
    public void recoverStaleCompletions() {
        if (schedulerLock != null && !schedulerLock.tryAcquire("pos-completion-recovery", Duration.ofMinutes(2))) {
            log.debug("recoverStaleCompletions: skipped — another instance holds the lock");
            return;
        }
        try {
            doRecoverStaleCompletions();
        } finally {
            if (schedulerLock != null) {
                schedulerLock.release("pos-completion-recovery");
            }
        }
    }

    private void doRecoverStaleCompletions() {
        Instant cutoff = clock.instant().minus(completionRecoveryAge);
        for (OperationalShardAccess shardAccess : operationalShardRegistry.allShards()) {
            log.debug("recoverStaleCompletions: scanning shard {}", shardAccess.shardId().value());
            List<StaleCompletingOrder> staleOrders = shardAccess.jdbc().query("""
                    SELECT id, region_id, outlet_id
                    FROM pos.sale_order
                    WHERE status = :status
                      AND updated_at <= :cutoff
                    ORDER BY updated_at ASC, id ASC
                    LIMIT :limit
                    """, PosSql.params(
                    "status", SaleOrderStatus.COMPLETING.name(),
                    "cutoff", cutoff,
                    "limit", completionRecoveryBatchSize
            ), (rs, rowNum) -> new StaleCompletingOrder(
                    rs.getLong("id"),
                    rs.getLong("region_id"),
                    rs.getLong("outlet_id")
            ));
            for (StaleCompletingOrder staleOrder : staleOrders) {
                recoverStaleCompletion(staleOrder);
            }
        }
    }

    public SaleOrderResponse cancelOrder(FernPrincipal principal, Long id) {
        OrderRecord order = store.requireOrder(rootJdbcTemplate(), id);
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(order.regionId(), order.outletId());
        TransactionTemplate transactionTemplate = transactionTemplate(order.regionId(), order.outletId());
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_CANCEL);
        ensureOrderOpen(order);
        if (store.successfulPaymentTotal(jdbcTemplate, id).compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Orders with successful payments cannot be cancelled");
        }
        // AUD-006: holder to capture before-snapshot built inside the transaction
        @SuppressWarnings("unchecked")
        Map<String, Object>[] beforeSnapshotHolder = new Map[1];
        transactionTemplate.executeWithoutResult(status -> {
            // AUD-006: capture before snapshot inside transaction to avoid race condition with concurrent updates
            OrderRecord currentOrder = store.requireOrderForUpdate(jdbcTemplate, id);
            ensureOrderOpen(currentOrder);
            if (store.successfulPaymentTotal(jdbcTemplate, id).compareTo(BigDecimal.ZERO) > 0) {
                throw new ConflictException("Orders with successful payments cannot be cancelled");
            }
            beforeSnapshotHolder[0] = Map.of(
                    "orderId", currentOrder.id(),
                    "orderNumber", currentOrder.orderNumber(),
                    "status", currentOrder.status(),
                    "totalAmount", currentOrder.totalAmount(),
                    "lineCount", store.queryOrderLines(jdbcTemplate, currentOrder.id()).size()
            );
            jdbcTemplate.update("""
                    UPDATE pos.sale_order
                    SET status = :status,
                        cancelled_at = :cancelledAt,
                        cancelled_by_user_id = :cancelledByUserId,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, PosSql.params(
                    "status", SaleOrderStatus.CANCELLED.name(),
                    "cancelledAt", clock.instant(),
                    "cancelledByUserId", principal.userId(),
                    "id", id
            ));
        });
        // AUD-006: publish cancel audit with before/after snapshot
        posAuditService.publishOrderEvent(
                "pos.order.cancelled.audit",
                principal,
                order.regionId(),
                order.outletId(),
                "CANCEL_ORDER",
                id,
                beforeSnapshotHolder[0],
                Map.of("status", SaleOrderStatus.CANCELLED.name()),
                Map.of("orderNumber", order.orderNumber(), "sessionId", order.posSessionId())
        );
        return getOrder(principal, id);
    }

    private void enqueueSaleCompletedEvent(
            NamedParameterJdbcTemplate jdbcTemplate,
            OrderRecord order,
            SessionRecord session,
            PricingSnapshot pricingSnapshot,
            List<SalePaymentResponse> payments,
            Map<String, Object> saleSnapshot,
            List<RecipeUsageItem> usageItems,
            SaleReservationResponse reservation,
            FernPrincipal principal,
            Instant completedAt,
            String correlationId
    ) {
        PosSaleCompletedEvent event = new PosSaleCompletedEvent(
                UUID.randomUUID().toString(),
                PosEventTypes.SALE_COMPLETED,
                completedAt,
                PosServiceNames.POS_SERVICE,
                correlationId,
                saleCompletedIdempotencyKey(order.id()),
                order.id(),
                order.posSessionId(),
                order.regionId(),
                order.outletId(),
                session.businessDate(),
                completedAt,
                principal.userId(),
                reservation.reservationId(),
                payments.stream()
                        .map(payment -> new SalePaymentSnapshot(
                                payment.id(),
                                payment.paymentMethod(),
                                payment.amount(),
                                payment.status(),
                                payment.paymentTime(),
                                payment.transactionRef()
                        ))
                        .toList(),
                saleSnapshot,
                usageItems
        );
        jdbcTemplate.update("""
                INSERT INTO pos.outbox_event (
                    id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, retry_count, created_at
                ) VALUES (
                    CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), :status, 0, CURRENT_TIMESTAMP
                )
                """, PosSql.params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", "SALE_ORDER",
                "aggregateId", order.id().toString(),
                "eventType", PosEventTypes.SALE_COMPLETED,
                "partitionKey", order.outletId().toString(),
                "payload", toJson(event),
                "status", PosOutboxStatus.PENDING.name()
        ));
    }

    private String saleCompletedIdempotencyKey(Long saleOrderId) {
        return PosEventTypes.SALE_COMPLETED + ":sale:" + saleOrderId;
    }

    private PricingSnapshot pricingSnapshotFromStoredOrder(OrderRecord order, List<SaleOrderLineResponse> lines) {
        return new PricingSnapshot(
                toPricedLines(lines),
                order.subtotal(),
                order.discountAmount(),
                order.taxAmount(),
                order.totalAmount(),
                null // promotion code not needed for stored order reconstruction
        );
    }

    private CompletionPreflight prepareCompletionPreflight(
            FernPrincipal principal,
            Long orderId,
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        OrderRecord currentOrder = store.requireOrderForCompletionPreflight(jdbcTemplate, orderId);
        if (isCompletionReplay(currentOrder)) {
            return new CompletionPreflight(
                    currentOrder,
                    store.requireSession(jdbcTemplate, currentOrder.posSessionId()),
                    pricingSnapshotFromStoredOrder(currentOrder, store.queryOrderLines(jdbcTemplate, orderId)),
                    List.of(),
                    List.of()
            );
        }
        ensureOrderOpenForCompletion(currentOrder);
        if (store.successfulPaymentTotal(jdbcTemplate, orderId).compareTo(currentOrder.totalAmount()) < 0) {
            throw new ConflictException("Order cannot be completed until payment covers the full total");
        }
        SessionRecord currentSession = store.requireSession(jdbcTemplate, currentOrder.posSessionId());
        List<SaleOrderLineResponse> orderLines = store.queryOrderLines(jdbcTemplate, orderId);
        PricingSnapshot pricingSnapshot = pricingSnapshotFromStoredOrder(currentOrder, orderLines);
        List<RecipeSnapshot> recipeSnapshots = pricingService.resolveRecipeSnapshots(
                principal,
                pricingSnapshot.lines(),
                currentSession.businessDate()
        );
        List<RecipeUsageItem> usageItems = pricingService.flattenUsage(pricingSnapshot.lines(), recipeSnapshots);
        jdbcTemplate.update("""
                UPDATE pos.sale_order
                SET status = :status,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND status = :currentStatus
                """, PosSql.params(
                "status", SaleOrderStatus.COMPLETING.name(),
                "id", orderId,
                "currentStatus", SaleOrderStatus.OPEN.name()
        ));
        OrderRecord markedOrder = store.requireOrder(jdbcTemplate, orderId);
        return new CompletionPreflight(markedOrder, currentSession, pricingSnapshot, recipeSnapshots, usageItems);
    }

    private void revertCompletingOrder(Long regionId, Long outletId, Long orderId) {
        transactionTemplate(regionId, outletId).executeWithoutResult(status -> {
            NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplate(regionId, outletId);
            int updated = jdbcTemplate.update("""
                    UPDATE pos.sale_order
                    SET status = :status,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                      AND status = :currentStatus
                    """, PosSql.params(
                    "status", SaleOrderStatus.OPEN.name(),
                    "id", orderId,
                    "currentStatus", SaleOrderStatus.COMPLETING.name()
            ));
            if (updated == 1) {
                store.refreshPaymentStatus(jdbcTemplate, orderId);
            }
        });
    }

    private void recoverStaleCompletion(StaleCompletingOrder staleOrder) {
        try {
            inventoryClient.releaseInventoryReservationBySourceOrderId(SYSTEM_PRINCIPAL, staleOrder.orderId());
            revertCompletingOrder(staleOrder.regionId(), staleOrder.outletId(), staleOrder.orderId());
            completionRecoveryCounter.increment();
            log.warn(
                    "Recovered stale POS order completion orderId={} regionId={} outletId={}",
                    staleOrder.orderId(),
                    staleOrder.regionId(),
                    staleOrder.outletId()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to recover stale POS order completion orderId={} regionId={} outletId={}",
                    staleOrder.orderId(),
                    staleOrder.regionId(),
                    staleOrder.outletId(),
                    exception
            );
        }
    }

    private NamedParameterJdbcTemplate rootJdbcTemplate() {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(0L, 0L))).jdbc();
    }

    private NamedParameterJdbcTemplate jdbcTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).jdbc();
    }

    private TransactionTemplate transactionTemplate(Long regionId, Long outletId) {
        return operationalShardRegistry.get(shardResolver.resolve(RouteKey.of(regionId, outletId))).tx();
    }

    private boolean isCompletionReplay(OrderRecord order) {
        return SaleOrderStatus.COMPLETED.name().equals(order.status()) && order.reservationId() != null;
    }

    private void ensureOrderOpenForCompletion(OrderRecord order) {
        if (SaleOrderStatus.COMPLETING.name().equals(order.status())) {
            throw new ConflictException("Order completion is already in progress");
        }
        ensureOrderOpen(order);
    }

    private void ensureOrderCompleting(OrderRecord order) {
        if (!SaleOrderStatus.COMPLETING.name().equals(order.status())) {
            throw new ConflictException("Order completion is not in progress");
        }
    }

    private List<PricedLine> toPricedLines(List<SaleOrderLineResponse> lines) {
        return lines.stream()
                .map(line -> new PricedLine(
                        line.lineNumber(),
                        line.productId(),
                        line.productCode(),
                        line.productNameSnapshot(),
                        line.unitPrice(),
                        line.qty(),
                        line.discountAmount(),
                        line.taxAmount(),
                        line.lineTotal(),
                        line.note()
                ))
                .toList();
    }

    private Map<String, Object> buildSaleSnapshot(
            OrderRecord order,
            PricingSnapshot pricingSnapshot,
            List<SalePaymentResponse> payments,
            List<RecipeSnapshot> recipeSnapshots,
            Long reservationId
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderId", order.id());
        snapshot.put("orderNumber", order.orderNumber());
        snapshot.put("outletId", order.outletId());
        snapshot.put("sessionId", order.posSessionId());
        snapshot.put("currencyCode", order.currencyCode());
        snapshot.put("subtotal", pricingSnapshot.subtotal());
        snapshot.put("taxAmount", pricingSnapshot.taxAmount());
        snapshot.put("totalAmount", pricingSnapshot.totalAmount());
        snapshot.put("reservationId", reservationId);
        snapshot.put("lines", pricingSnapshot.lines());
        snapshot.put("payments", payments);
        snapshot.put("recipes", recipeSnapshots);
        return snapshot;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    private void ensureSessionOpen(SessionRecord session) {
        if (!PosSessionStatus.OPEN.name().equals(session.status())) {
            throw new ConflictException("The POS session is not open");
        }
    }

    private void ensureOutletOperational(Long outletId, LocalDate businessDate) {
        PosOrgClient.OutletRoute outlet = posOrgClient.requireOutlet(outletId);
        if (!outlet.isActive() || outlet.isClosedOn(businessDate)) {
            throw new ConflictException("Outlet is inactive or closed for POS transactions");
        }
    }

    private void ensureOrderOpen(OrderRecord order) {
        if (!SaleOrderStatus.OPEN.name().equals(order.status())) {
            throw new ConflictException("Only open orders can be modified");
        }
    }

    private record CompletionPreflight(
            OrderRecord order,
            SessionRecord session,
            PricingSnapshot pricingSnapshot,
            List<RecipeSnapshot> recipeSnapshots,
            List<RecipeUsageItem> usageItems
    ) {
    }

    private record ExistingPayment(
            Long id,
            Long saleOrderId,
            String paymentMethod,
            BigDecimal amount,
            String status,
            Instant paymentTime,
            String transactionRef,
            String note
    ) {
    }

    private void ensureNoSuccessfulPayments(NamedParameterJdbcTemplate jdbcTemplate, Long orderId) {
        if (store.successfulPaymentTotal(jdbcTemplate, orderId).compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Orders with successful payments cannot be updated");
        }
    }

    private void releaseReservationAfterFailure(FernPrincipal principal, SaleReservationResponse reservation, RuntimeException originalException) {
        if (reservation == null || reservation.reservationId() == null) {
            return;
        }
        try {
            inventoryClient.releaseInventoryReservation(principal, reservation.reservationId());
        } catch (RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private record StaleCompletingOrder(
            Long orderId,
            Long regionId,
            Long outletId
    ) {
    }
}
