package com.fern.posservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.posservice.dto.PosCommands.AddPaymentRequest;
import com.fern.posservice.dto.PosCommands.CreateSaleOrderRequest;
import com.fern.posservice.dto.PosCommands.OrderLineInput;
import com.fern.posservice.dto.PosCommands.UpdateSaleOrderRequest;
import com.fern.posservice.dto.PosResponses.SaleOrderLineResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import com.fern.posservice.dto.PosResponses.SalePaymentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PosOrderService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PosAuthorizer posAuthorizer;
    private final PosStore store;
    private final PosPricingService pricingService;
    private final PosInventoryClient inventoryClient;
    private final PosReferenceCodeGenerator codeGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OperationalAlertPublisher operationalAlertPublisher;
    private final Counter paymentFailureCounter;

    public PosOrderService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PosAuthorizer posAuthorizer,
            PosStore store,
            PosPricingService pricingService,
            PosInventoryClient inventoryClient,
            PosReferenceCodeGenerator codeGenerator,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            OperationalAlertPublisher operationalAlertPublisher,
            MeterRegistry meterRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.posAuthorizer = posAuthorizer;
        this.store = store;
        this.pricingService = pricingService;
        this.inventoryClient = inventoryClient;
        this.codeGenerator = codeGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.operationalAlertPublisher = operationalAlertPublisher;
        this.paymentFailureCounter = Counter.builder("fern_payment_failures_total").register(meterRegistry);
    }

    public SaleOrderResponse createOrder(FernPrincipal principal, CreateSaleOrderRequest request) {
        SessionRecord session = store.requireSession(request.posSessionId());
        posAuthorizer.requireRoutePermission(principal, session.regionId(), session.outletId(), PermissionCodes.POS_ORDER_CREATE);
        ensureSessionOpen(session);
        PricingSnapshot pricingSnapshot = pricingService.resolvePricingSnapshot(
                principal,
                session.outletId(),
                session.businessDate(),
                request.lines()
        );
        Long id = Objects.requireNonNull(transactionTemplate.execute(status -> {
            SessionRecord currentSession = store.requireSessionForUpdate(request.posSessionId());
            ensureSessionOpen(currentSession);
            Long orderId = PosSql.insertForId(jdbcTemplate, """
                    INSERT INTO pos.sale_order (
                        order_number, region_id, outlet_id, pos_session_id, currency_code, order_type, status,
                        payment_status, subtotal, discount_amount, tax_amount, total_amount, note, created_at, updated_at
                    ) VALUES (
                        :orderNumber, :regionId, :outletId, :sessionId, :currencyCode, :orderType, :status,
                        :paymentStatus, :subtotal, 0, :taxAmount, :totalAmount, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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
                    "taxAmount", pricingSnapshot.taxAmount(),
                    "totalAmount", pricingSnapshot.totalAmount(),
                    "note", request.note()
            ));
            store.replaceOrderLines(orderId, pricingSnapshot.lines());
            return orderId;
        }));
        return getOrder(principal, id);
    }

    public SaleOrderResponse getOrder(FernPrincipal principal, Long id) {
        OrderRecord order = store.requireOrder(id);
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_READ);
        return store.mapOrder(order);
    }

    public SaleOrderResponse updateOrder(FernPrincipal principal, Long id, UpdateSaleOrderRequest request) {
        OrderRecord order = store.requireOrder(id);
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_UPDATE);
        ensureOrderOpen(order);
        ensureNoSuccessfulPayments(id);
        SessionRecord session = store.requireSession(order.posSessionId());
        PricingSnapshot pricingSnapshot = pricingService.resolvePricingSnapshot(
                principal,
                order.outletId(),
                session.businessDate(),
                request.lines()
        );
        transactionTemplate.executeWithoutResult(status -> {
            OrderRecord currentOrder = store.requireOrderForUpdate(id);
            ensureOrderOpen(currentOrder);
            ensureNoSuccessfulPayments(id);
            jdbcTemplate.update("""
                    UPDATE pos.sale_order
                    SET subtotal = :subtotal,
                        tax_amount = :taxAmount,
                        total_amount = :totalAmount,
                        note = :note,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, PosSql.params(
                    "subtotal", pricingSnapshot.subtotal(),
                    "taxAmount", pricingSnapshot.taxAmount(),
                    "totalAmount", pricingSnapshot.totalAmount(),
                    "note", request.note(),
                    "id", id
            ));
            store.replaceOrderLines(id, pricingSnapshot.lines());
            store.refreshPaymentStatus(id);
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
        OrderRecord order = store.requireOrder(id);
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_UPDATE);
        ensureOrderOpen(order);
        transactionTemplate.executeWithoutResult(status -> {
            OrderRecord currentOrder = store.requireOrderForUpdate(id);
            ensureOrderOpen(currentOrder);
            Long existingPaymentId = jdbcTemplate.query("""
                    SELECT id
                    FROM pos.sale_payment
                    WHERE idempotency_key = :idempotencyKey
                    """, PosSql.params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
            if (existingPaymentId != null) {
                return;
            }
            SalePaymentStatus paymentStatus = resolvePaymentStatus(request.status());
            if (paymentStatus == SalePaymentStatus.SUCCESS) {
                BigDecimal currentSuccessAmount = store.successfulPaymentTotal(id);
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
            store.refreshPaymentStatus(id);
        });
        return getOrder(principal, id);
    }

    private SalePaymentStatus resolvePaymentStatus(String rawStatus) {
        String normalized = rawStatus == null || rawStatus.isBlank()
                ? SalePaymentStatus.SUCCESS.name()
                : rawStatus.trim().toUpperCase(Locale.ROOT);
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

    public SaleOrderResponse completeOrder(FernPrincipal principal, Long id, String correlationId) {
        OrderRecord order = store.requireOrder(id);
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_COMPLETE);
        ensureOrderOpen(order);
        if (store.successfulPaymentTotal(id).compareTo(order.totalAmount()) < 0) {
            throw new ConflictException("Order cannot be completed until payment covers the full total");
        }
        SessionRecord session = store.requireSession(order.posSessionId());
        PricingSnapshot pricingSnapshot = pricingService.resolvePricingSnapshot(
                principal,
                order.outletId(),
                session.businessDate(),
                toOrderLineInputs(store.queryOrderLines(id))
        );
        List<RecipeSnapshot> recipeSnapshots = pricingService.resolveRecipeSnapshots(principal, pricingSnapshot.lines(), session.businessDate());
        List<RecipeUsageItem> usageItems = pricingService.flattenUsage(pricingSnapshot.lines(), recipeSnapshots);
        SaleReservationResponse reservation = inventoryClient.reserveInventory(
                principal,
                order.outletId(),
                session.businessDate(),
                id,
                usageItems
        );
        try {
            List<SalePaymentResponse> payments = store.queryPayments(id);
            Map<String, Object> saleSnapshot = buildSaleSnapshot(order, pricingSnapshot, payments, recipeSnapshots, reservation.reservationId());
            Instant completedAt = clock.instant();
            transactionTemplate.executeWithoutResult(status -> {
                OrderRecord currentOrder = store.requireOrderForUpdate(id);
                ensureOrderOpen(currentOrder);
                if (store.successfulPaymentTotal(id).compareTo(currentOrder.totalAmount()) < 0) {
                    throw new ConflictException("Order cannot be completed until payment covers the full total");
                }
                SessionRecord currentSession = store.requireSession(currentOrder.posSessionId());
                store.replaceOrderLines(id, pricingSnapshot.lines());
                jdbcTemplate.update("""
                        INSERT INTO pos.sale_snapshot (sale_order_id, order_snapshot, created_at)
                        VALUES (:saleOrderId, CAST(:orderSnapshot AS jsonb), CURRENT_TIMESTAMP)
                        ON CONFLICT (sale_order_id) DO UPDATE
                        SET order_snapshot = EXCLUDED.order_snapshot
                        """, PosSql.params("saleOrderId", id, "orderSnapshot", toJson(saleSnapshot)));
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
                        "subtotal", pricingSnapshot.subtotal(),
                        "taxAmount", pricingSnapshot.taxAmount(),
                        "totalAmount", pricingSnapshot.totalAmount(),
                        "completedAt", completedAt,
                        "completedByUserId", principal.userId(),
                        "reservationId", reservation.reservationId(),
                        "id", id
                ));
                enqueueSaleCompletedEvent(
                        currentOrder,
                        currentSession,
                        pricingSnapshot,
                        payments,
                        saleSnapshot,
                        usageItems,
                        reservation,
                        principal,
                        completedAt,
                        correlationId
                );
            });
        } catch (RuntimeException exception) {
            releaseReservationAfterFailure(principal, reservation, exception);
            throw exception;
        }
        return getOrder(principal, id);
    }

    public SaleOrderResponse cancelOrder(FernPrincipal principal, Long id) {
        OrderRecord order = store.requireOrder(id);
        posAuthorizer.requireRoutePermission(principal, order.regionId(), order.outletId(), PermissionCodes.POS_ORDER_CANCEL);
        ensureOrderOpen(order);
        if (store.successfulPaymentTotal(id).compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Orders with successful payments cannot be cancelled");
        }
        transactionTemplate.executeWithoutResult(status -> {
            OrderRecord currentOrder = store.requireOrderForUpdate(id);
            ensureOrderOpen(currentOrder);
            if (store.successfulPaymentTotal(id).compareTo(BigDecimal.ZERO) > 0) {
                throw new ConflictException("Orders with successful payments cannot be cancelled");
            }
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
        return getOrder(principal, id);
    }

    private void enqueueSaleCompletedEvent(
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
                UUID.randomUUID().toString(),
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

    private List<OrderLineInput> toOrderLineInputs(List<SaleOrderLineResponse> lines) {
        return lines.stream()
                .map(line -> new OrderLineInput(line.productId(), line.qty(), line.note()))
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

    private void ensureOrderOpen(OrderRecord order) {
        if (!SaleOrderStatus.OPEN.name().equals(order.status())) {
            throw new ConflictException("Only open orders can be modified");
        }
    }

    private void ensureNoSuccessfulPayments(Long orderId) {
        if (store.successfulPaymentTotal(orderId).compareTo(BigDecimal.ZERO) > 0) {
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
}
