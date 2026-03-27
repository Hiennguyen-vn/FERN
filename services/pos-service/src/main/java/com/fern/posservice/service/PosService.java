package com.fern.posservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.RecipeUsageItem;
import com.fern.platform.contracts.SalePaymentSnapshot;
import com.fern.platform.contracts.SaleReservationItem;
import com.fern.platform.contracts.SaleReservationRequest;
import com.fern.platform.contracts.SaleReservationResponse;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import com.fern.posservice.config.PosClientProperties;
import com.fern.posservice.dto.PosCommands.AddPaymentRequest;
import com.fern.posservice.dto.PosCommands.CreateSaleOrderRequest;
import com.fern.posservice.dto.PosCommands.OpenSessionRequest;
import com.fern.posservice.dto.PosCommands.OrderLineInput;
import com.fern.posservice.dto.PosCommands.ReconcileSessionRequest;
import com.fern.posservice.dto.PosCommands.UpdateSaleOrderRequest;
import com.fern.posservice.dto.PosResponses.PosSessionResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderLineResponse;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import com.fern.posservice.dto.PosResponses.SalePaymentResponse;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class PosService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PosAuthorizer posAuthorizer;
    private final RestClient restClient;
    private final PosClientProperties clientProperties;
    private final FernJwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PosService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PosAuthorizer posAuthorizer,
            RestClient restClient,
            PosClientProperties clientProperties,
            FernJwtService jwtService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.posAuthorizer = posAuthorizer;
        this.restClient = restClient;
        this.clientProperties = clientProperties;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public PosSessionResponse openSession(FernPrincipal principal, OpenSessionRequest request) {
        posAuthorizer.requireOutletPermission(principal, request.outletId(), PermissionCodes.POS_SESSION_OPEN);
        boolean openExists;
        if (request.terminalId() != null) {
            openExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pos.pos_session
                        WHERE outlet_id = :outletId
                          AND terminal_id = :terminalId
                          AND status = 'OPEN'
                    )
                    """, params("outletId", request.outletId(), "terminalId", request.terminalId()), Boolean.class));
        } else {
            openExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM pos.pos_session
                        WHERE outlet_id = :outletId
                          AND status = 'OPEN'
                          AND terminal_id IS NULL
                    )
                    """, params("outletId", request.outletId()), Boolean.class));
        }
        if (openExists) {
            throw new ConflictException("The outlet already has an open POS session");
        }
        Long id = insertForId("""
                INSERT INTO pos.pos_session (
                    session_code, region_id, outlet_id, currency_code, cashier_user_id, manager_user_id,
                    terminal_id, opened_at, business_date, status, note, created_at, updated_at
                ) VALUES (
                    :sessionCode, :regionId, :outletId, :currencyCode, :cashierUserId, NULL,
                    :terminalId, :openedAt, :businessDate, 'OPEN', :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "sessionCode", "POSS-" + Instant.now(clock).toEpochMilli(),
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "currencyCode", request.currencyCode(),
                "cashierUserId", principal.userId(),
                "terminalId", request.terminalId(),
                "openedAt", Instant.now(clock),
                "businessDate", request.businessDate(),
                "note", request.note()
        ));
        return getSession(principal, id);
    }

    @Transactional(readOnly = true)
    public PosSessionResponse getSession(FernPrincipal principal, Long id) {
        SessionRecord session = requireSession(id);
        posAuthorizer.requireOutletPermission(principal, session.outletId(), PermissionCodes.POS_SESSION_READ);
        return mapSession(session);
    }

    @Transactional(readOnly = true)
    public List<PosSessionResponse> listSessions(FernPrincipal principal, Long outletId, String status, LocalDate businessDate) {
        posAuthorizer.requireOutletPermission(principal, outletId, PermissionCodes.POS_SESSION_READ);
        String terminalId = currentTerminalIdFilter();
        return jdbcTemplate.query("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE outlet_id = :outletId
                  AND (CAST(:terminalId AS VARCHAR) IS NULL OR terminal_id = CAST(:terminalId AS VARCHAR))
                  AND (:status IS NULL OR status = :status)
                  AND (:businessDate IS NULL OR business_date = :businessDate)
                ORDER BY opened_at DESC
                """, params("outletId", outletId, "terminalId", terminalId, "status", status, "businessDate", businessDate), (rs, rowNum) -> new PosSessionResponse(
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
                instant(rs, "opened_at"),
                instant(rs, "closed_at"),
                instant(rs, "reconciled_at"),
                rs.getBigDecimal("expected_cash_amount"),
                rs.getBigDecimal("counted_cash_amount"),
                rs.getBigDecimal("discrepancy_amount")
        ));
    }

    @Transactional
    public PosSessionResponse closeSession(FernPrincipal principal, Long id) {
        SessionRecord session = requireSession(id);
        posAuthorizer.requireOutletPermission(principal, session.outletId(), PermissionCodes.POS_SESSION_CLOSE);
        if (!"OPEN".equals(session.status())) {
            throw new ConflictException("Only open sessions can be closed");
        }
        boolean openOrders = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pos.sale_order
                    WHERE pos_session_id = :sessionId AND status = 'OPEN'
                )
                """, params("sessionId", id), Boolean.class));
        if (openOrders) {
            throw new ConflictException("Cannot close a POS session while open orders still exist");
        }
        jdbcTemplate.update("""
                UPDATE pos.pos_session
                SET status = 'CLOSED', closed_at = :closedAt, manager_user_id = :managerUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("closedAt", Instant.now(clock), "managerUserId", principal.userId(), "id", id));
        return getSession(principal, id);
    }

    @Transactional
    public PosSessionResponse reconcileSession(FernPrincipal principal, Long id, ReconcileSessionRequest request) {
        SessionRecord session = requireSession(id);
        posAuthorizer.requireOutletPermission(principal, session.outletId(), PermissionCodes.POS_SESSION_RECONCILE);
        if (!"CLOSED".equals(session.status())) {
            throw new ConflictException("Only closed sessions can be reconciled");
        }
        BigDecimal expectedCash = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(payment.amount), 0)
                FROM pos.sale_payment payment
                JOIN pos.sale_order sale_order ON sale_order.id = payment.sale_order_id
                WHERE payment.pos_session_id = :sessionId
                  AND payment.status = 'SUCCESS'
                  AND payment.payment_method = 'CASH'
                  AND sale_order.status = 'COMPLETED'
                """, params("sessionId", id), BigDecimal.class);
        BigDecimal discrepancy = request.countedCashAmount().subtract(expectedCash);
        jdbcTemplate.update("""
                UPDATE pos.pos_session
                SET status = 'RECONCILED',
                    manager_user_id = :managerUserId,
                    reconciled_at = :reconciledAt,
                    expected_cash_amount = :expectedCashAmount,
                    counted_cash_amount = :countedCashAmount,
                    discrepancy_amount = :discrepancyAmount,
                    note = COALESCE(:note, note),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "managerUserId", principal.userId(),
                "reconciledAt", Instant.now(clock),
                "expectedCashAmount", expectedCash,
                "countedCashAmount", request.countedCashAmount(),
                "discrepancyAmount", discrepancy,
                "note", request.note(),
                "id", id
        ));
        return getSession(principal, id);
    }

    @Transactional
    public SaleOrderResponse createOrder(FernPrincipal principal, CreateSaleOrderRequest request) {
        SessionRecord session = requireSession(request.posSessionId());
        posAuthorizer.requireOutletPermission(principal, session.outletId(), PermissionCodes.POS_ORDER_CREATE);
        ensureSessionOpen(session);
        PricingSnapshot pricingSnapshot = resolvePricingSnapshot(principal, session.outletId(), session.businessDate(), request.lines());
        Long id = insertForId("""
                INSERT INTO pos.sale_order (
                    order_number, region_id, outlet_id, pos_session_id, currency_code, order_type, status,
                    payment_status, subtotal, discount_amount, tax_amount, total_amount, note, created_at, updated_at
                ) VALUES (
                    :orderNumber, :regionId, :outletId, :sessionId, :currencyCode, :orderType, 'OPEN',
                    'UNPAID', :subtotal, 0, :taxAmount, :totalAmount, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, params(
                "orderNumber", "SO-" + Instant.now(clock).toEpochMilli(),
                "regionId", session.regionId(),
                "outletId", session.outletId(),
                "sessionId", session.id(),
                "currencyCode", session.currencyCode(),
                "orderType", request.orderType(),
                "subtotal", pricingSnapshot.subtotal(),
                "taxAmount", pricingSnapshot.taxAmount(),
                "totalAmount", pricingSnapshot.totalAmount(),
                "note", request.note()
        ));
        replaceOrderLines(id, pricingSnapshot.lines());
        return getOrder(principal, id);
    }

    @Transactional(readOnly = true)
    public SaleOrderResponse getOrder(FernPrincipal principal, Long id) {
        OrderRecord order = requireOrder(id);
        posAuthorizer.requireOutletPermission(principal, order.outletId(), PermissionCodes.POS_ORDER_READ);
        return mapOrder(order);
    }

    @Transactional
    public SaleOrderResponse updateOrder(FernPrincipal principal, Long id, UpdateSaleOrderRequest request) {
        OrderRecord order = requireOrder(id);
        posAuthorizer.requireOutletPermission(principal, order.outletId(), PermissionCodes.POS_ORDER_UPDATE);
        ensureOrderOpen(order);
        SessionRecord session = requireSession(order.posSessionId());
        PricingSnapshot pricingSnapshot = resolvePricingSnapshot(principal, order.outletId(), session.businessDate(), request.lines());
        jdbcTemplate.update("""
                UPDATE pos.sale_order
                SET subtotal = :subtotal,
                    tax_amount = :taxAmount,
                    total_amount = :totalAmount,
                    note = :note,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "subtotal", pricingSnapshot.subtotal(),
                "taxAmount", pricingSnapshot.taxAmount(),
                "totalAmount", pricingSnapshot.totalAmount(),
                "note", request.note(),
                "id", id
        ));
        replaceOrderLines(id, pricingSnapshot.lines());
        refreshPaymentStatus(id);
        return getOrder(principal, id);
    }

    @Transactional
    public SaleOrderResponse addPayment(FernPrincipal principal, Long id, String idempotencyKey, AddPaymentRequest request) {
        requireIdempotencyKey(idempotencyKey);
        OrderRecord order = requireOrder(id);
        posAuthorizer.requireOutletPermission(principal, order.outletId(), PermissionCodes.POS_ORDER_CREATE);
        ensureOrderOpen(order);
        Long existingPaymentId = jdbcTemplate.query("""
                SELECT id
                FROM pos.sale_payment
                WHERE idempotency_key = :idempotencyKey
                """, params("idempotencyKey", idempotencyKey), rs -> rs.next() ? rs.getLong("id") : null);
        if (existingPaymentId != null) {
            return getOrder(principal, id);
        }
        String paymentStatus = request.status() == null || request.status().isBlank() ? "SUCCESS" : request.status();
        if (!List.of("SUCCESS", "FAILED", "CANCELLED").contains(paymentStatus)) {
            throw new BadRequestException("Unsupported payment status");
        }
        if ("SUCCESS".equals(paymentStatus)) {
            BigDecimal currentSuccessAmount = successfulPaymentTotal(id);
            if (currentSuccessAmount.add(request.amount()).compareTo(order.totalAmount()) > 0) {
                throw new ConflictException("Successful payments cannot exceed order total");
            }
        }
        insertForId("""
                INSERT INTO pos.sale_payment (
                    sale_order_id, pos_session_id, payment_method, amount, status, payment_time, transaction_ref, note, created_at, idempotency_key
                ) VALUES (
                    :saleOrderId, :sessionId, :paymentMethod, :amount, :status, :paymentTime, :transactionRef, :note, CURRENT_TIMESTAMP, :idempotencyKey
                )
                """, params(
                "saleOrderId", id,
                "sessionId", order.posSessionId(),
                "paymentMethod", request.paymentMethod(),
                "amount", request.amount(),
                "status", paymentStatus,
                "paymentTime", request.paymentTime() == null ? Instant.now(clock) : request.paymentTime(),
                "transactionRef", request.transactionRef(),
                "note", request.note(),
                "idempotencyKey", idempotencyKey
        ));
        refreshPaymentStatus(id);
        return getOrder(principal, id);
    }

    @Transactional
    public SaleOrderResponse completeOrder(FernPrincipal principal, Long id) {
        OrderRecord order = requireOrder(id);
        posAuthorizer.requireOutletPermission(principal, order.outletId(), PermissionCodes.POS_ORDER_COMPLETE);
        ensureOrderOpen(order);
        if (successfulPaymentTotal(id).compareTo(order.totalAmount()) < 0) {
            throw new ConflictException("Order cannot be completed until payment covers the full total");
        }
        SessionRecord session = requireSession(order.posSessionId());
        PricingSnapshot pricingSnapshot = resolvePricingSnapshot(principal, order.outletId(), session.businessDate(), toOrderLineInputs(queryOrderLines(id)));
        replaceOrderLines(id, pricingSnapshot.lines());
        List<RecipeSnapshot> recipeSnapshots = resolveRecipeSnapshots(principal, pricingSnapshot.lines(), session.businessDate());
        List<RecipeUsageItem> usageItems = flattenUsage(pricingSnapshot.lines(), recipeSnapshots);
        SaleReservationResponse reservation = reserveInventory(principal, order.outletId(), session.businessDate(), id, usageItems);
        List<SalePaymentResponse> payments = queryPayments(id);
        Map<String, Object> saleSnapshot = buildSaleSnapshot(order, pricingSnapshot, payments, recipeSnapshots, reservation.reservationId());
        jdbcTemplate.update("""
                INSERT INTO pos.sale_snapshot (sale_order_id, order_snapshot, created_at)
                VALUES (:saleOrderId, CAST(:orderSnapshot AS jsonb), CURRENT_TIMESTAMP)
                ON CONFLICT (sale_order_id) DO UPDATE
                SET order_snapshot = EXCLUDED.order_snapshot
                """, params("saleOrderId", id, "orderSnapshot", toJson(saleSnapshot)));
        Instant completedAt = Instant.now(clock);
        jdbcTemplate.update("""
                UPDATE pos.sale_order
                SET status = 'COMPLETED',
                    payment_status = 'PAID',
                    subtotal = :subtotal,
                    tax_amount = :taxAmount,
                    total_amount = :totalAmount,
                    completed_at = :completedAt,
                    completed_by_user_id = :completedByUserId,
                    reservation_id = :reservationId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "subtotal", pricingSnapshot.subtotal(),
                "taxAmount", pricingSnapshot.taxAmount(),
                "totalAmount", pricingSnapshot.totalAmount(),
                "completedAt", completedAt,
                "completedByUserId", principal.userId(),
                "reservationId", reservation.reservationId(),
                "id", id
        ));
        enqueueSaleCompletedEvent(order, session, pricingSnapshot, payments, saleSnapshot, usageItems, reservation, principal, completedAt);
        return getOrder(principal, id);
    }

    @Transactional
    public SaleOrderResponse cancelOrder(FernPrincipal principal, Long id) {
        OrderRecord order = requireOrder(id);
        posAuthorizer.requireOutletPermission(principal, order.outletId(), PermissionCodes.POS_ORDER_CANCEL);
        ensureOrderOpen(order);
        if (successfulPaymentTotal(id).compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Orders with successful payments cannot be cancelled");
        }
        jdbcTemplate.update("""
                UPDATE pos.sale_order
                SET status = 'CANCELLED', cancelled_at = :cancelledAt, cancelled_by_user_id = :cancelledByUserId, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params(
                "cancelledAt", Instant.now(clock),
                "cancelledByUserId", principal.userId(),
                "id", id
        ));
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
            Instant completedAt
    ) {
        PosSaleCompletedEvent event = new PosSaleCompletedEvent(
                UUID.randomUUID().toString(),
                "pos.sale.completed",
                completedAt,
                "pos-service",
                currentCorrelationId(),
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
                        .map(payment -> new SalePaymentSnapshot(payment.id(), payment.paymentMethod(), payment.amount(), payment.status(), payment.paymentTime(), payment.transactionRef()))
                        .toList(),
                saleSnapshot,
                usageItems
        );
        jdbcTemplate.update("""
                INSERT INTO pos.outbox_event (id, aggregate_type, aggregate_id, event_type, partition_key, payload, status, created_at)
                VALUES (CAST(:id AS uuid), :aggregateType, :aggregateId, :eventType, :partitionKey, CAST(:payload AS jsonb), 'PENDING', CURRENT_TIMESTAMP)
                """, params(
                "id", UUID.randomUUID().toString(),
                "aggregateType", "SALE_ORDER",
                "aggregateId", order.id().toString(),
                "eventType", "pos.sale.completed",
                "partitionKey", order.outletId().toString(),
                "payload", toJson(event)
        ));
    }

    private PricingSnapshot resolvePricingSnapshot(
            FernPrincipal principal,
            Long outletId,
            LocalDate businessDate,
            List<OrderLineInput> requestedLines
    ) {
        MenuResponse menu = fetchMenu(principal, outletId, businessDate);
        Map<Long, MenuItem> menuItems = menu.items().stream().collect(Collectors.toMap(MenuItem::productId, item -> item));
        List<PricedLine> pricedLines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        int lineNumber = 1;
        for (OrderLineInput input : requestedLines) {
            MenuItem item = menuItems.get(input.productId());
            if (item == null) {
                throw new ConflictException("Product " + input.productId() + " is not active, available, or effectively priced for the outlet");
            }
            BigDecimal lineSubtotal = item.priceValue().multiply(input.qty()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(item.taxPercent().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax);
            pricedLines.add(new PricedLine(
                    lineNumber++,
                    item.productId(),
                    item.productCode(),
                    item.productName(),
                    item.priceValue(),
                    input.qty(),
                    BigDecimal.ZERO,
                    lineTax,
                    lineTotal,
                    input.note()
            ));
            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTax);
        }
        return new PricingSnapshot(pricedLines, subtotal, taxAmount, subtotal.add(taxAmount));
    }

    private List<RecipeSnapshot> resolveRecipeSnapshots(FernPrincipal principal, List<PricedLine> lines, LocalDate businessDate) {
        LinkedHashSet<Long> productIds = lines.stream().map(PricedLine::productId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        for (Long productId : productIds) {
            snapshots.add(fetchRecipe(principal, productId, businessDate));
        }
        return snapshots;
    }

    private List<RecipeUsageItem> flattenUsage(List<PricedLine> lines, List<RecipeSnapshot> recipeSnapshots) {
        Map<Long, RecipeUsageItem> aggregated = new LinkedHashMap<>();
        Map<Long, RecipeSnapshot> snapshotByProductId = recipeSnapshots.stream().collect(Collectors.toMap(RecipeSnapshot::productId, item -> item));
        for (PricedLine line : lines) {
            RecipeSnapshot recipeSnapshot = snapshotByProductId.get(line.productId());
            if (recipeSnapshot == null) {
                throw new ConflictException("Recipe snapshot not found for product " + line.productId());
            }
            for (RecipeIngredient ingredient : recipeSnapshot.ingredients()) {
                BigDecimal usageQty = ingredient.qty().multiply(line.qty()).setScale(4, RoundingMode.HALF_UP);
                aggregated.compute(ingredient.ingredientId(), (ingredientId, current) -> current == null
                        ? new RecipeUsageItem(ingredient.ingredientId(), ingredient.ingredientCode(), ingredient.ingredientName(), ingredient.uomCode(), usageQty)
                        : new RecipeUsageItem(
                                current.ingredientId(),
                                current.ingredientCode(),
                                current.ingredientName(),
                                current.uomCode(),
                                current.qty().add(usageQty).setScale(4, RoundingMode.HALF_UP)
                        ));
            }
        }
        return aggregated.values().stream().toList();
    }

    private SaleReservationResponse reserveInventory(
            FernPrincipal principal,
            Long outletId,
            LocalDate businessDate,
            Long saleOrderId,
            List<RecipeUsageItem> usageItems
    ) {
        try {
            return restClient.post()
                    .uri(clientProperties.getInventoryBaseUrl() + "/internal/inventory/sale-reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyInternalHeaders(headers, principal, Set.of(PermissionCodes.INVENTORY_INTERNAL_RESERVE)))
                    .body(new SaleReservationRequest(
                            outletId,
                            businessDate,
                            saleOrderId,
                            usageItems.stream()
                                    .map(item -> new SaleReservationItem(item.ingredientId(), item.ingredientCode(), item.ingredientName(), item.uomCode(), item.qty()))
                                    .toList()
                    ))
                    .retrieve()
                    .body(SaleReservationResponse.class);
        } catch (RestClientException exception) {
            throw new ConflictException("Inventory reservation failed: " + exception.getMessage());
        }
    }

    private MenuResponse fetchMenu(FernPrincipal principal, Long outletId, LocalDate businessDate) {
        return restClient.get()
                .uri(clientProperties.getCatalogBaseUrl() + "/internal/catalog/menu?outletId=" + outletId + "&at=" + businessDate)
                .headers(headers -> applyInternalHeaders(headers, principal, Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)))
                .retrieve()
                .body(MenuResponse.class);
    }

    private RecipeSnapshot fetchRecipe(FernPrincipal principal, Long productId, LocalDate businessDate) {
        return restClient.get()
                .uri(clientProperties.getCatalogBaseUrl() + "/internal/catalog/recipe-resolution?productId=" + productId + "&at=" + businessDate)
                .headers(headers -> applyInternalHeaders(headers, principal, Set.of(PermissionCodes.CATALOG_INTERNAL_RESOLVE)))
                .retrieve()
                .body(RecipeSnapshot.class);
    }

    private void applyInternalHeaders(HttpHeaders headers, FernPrincipal actor, Set<String> permissions) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + issueServiceToken(permissions));
        if (actor != null && actor.userId() != null) {
            headers.set(FernRequestHeaders.ACTOR_USER_ID, actor.userId().toString());
        }
        if (actor != null && actor.username() != null) {
            headers.set(FernRequestHeaders.ACTOR_USERNAME, actor.username());
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
    }

    private String issueServiceToken(Set<String> permissions) {
        Instant now = Instant.now(clock);
        return jwtService.encode(
                new FernJwtClaims(
                        null,
                        "pos-service",
                        Set.of(),
                        permissions,
                        new ScopeRoots(true, List.of(), List.of()),
                        0L,
                        0L,
                        UUID.randomUUID().toString(),
                        now,
                        now.plus(jwtService.serviceTokenTtl()),
                        FernPrincipalType.SERVICE
                ),
                jwtService.serviceTokenTtl()
        );
    }

    private void replaceOrderLines(Long saleOrderId, List<PricedLine> lines) {
        jdbcTemplate.update("DELETE FROM pos.sale_order_line WHERE sale_order_id = :saleOrderId", params("saleOrderId", saleOrderId));
        for (PricedLine line : lines) {
            jdbcTemplate.update("""
                    INSERT INTO pos.sale_order_line (
                        sale_order_id, line_number, product_id, product_code, product_name_snapshot,
                        unit_price, qty, discount_amount, tax_amount, line_total, note, created_at, updated_at
                    ) VALUES (
                        :saleOrderId, :lineNumber, :productId, :productCode, :productNameSnapshot,
                        :unitPrice, :qty, :discountAmount, :taxAmount, :lineTotal, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
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
            ));
        }
    }

    private void refreshPaymentStatus(Long saleOrderId) {
        BigDecimal successAmount = successfulPaymentTotal(saleOrderId);
        BigDecimal totalAmount = jdbcTemplate.queryForObject("SELECT total_amount FROM pos.sale_order WHERE id = :id", params("id", saleOrderId), BigDecimal.class);
        String paymentStatus = successAmount.compareTo(BigDecimal.ZERO) == 0
                ? "UNPAID"
                : successAmount.compareTo(totalAmount) >= 0 ? "PAID" : "PARTIALLY_PAID";
        jdbcTemplate.update("""
                UPDATE pos.sale_order
                SET payment_status = :paymentStatus, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("paymentStatus", paymentStatus, "id", saleOrderId));
    }

    private BigDecimal successfulPaymentTotal(Long saleOrderId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM pos.sale_payment
                WHERE sale_order_id = :saleOrderId AND status = 'SUCCESS'
                """, params("saleOrderId", saleOrderId), BigDecimal.class);
    }

    private List<SalePaymentResponse> queryPayments(Long saleOrderId) {
        return jdbcTemplate.query("""
                SELECT id, payment_method, amount, status, payment_time, transaction_ref
                FROM pos.sale_payment
                WHERE sale_order_id = :saleOrderId
                ORDER BY created_at, id
                """, params("saleOrderId", saleOrderId), (rs, rowNum) -> new SalePaymentResponse(
                rs.getLong("id"),
                rs.getString("payment_method"),
                rs.getBigDecimal("amount"),
                rs.getString("status"),
                instant(rs, "payment_time"),
                rs.getString("transaction_ref")
        ));
    }

    private List<OrderLineInput> toOrderLineInputs(List<SaleOrderLineResponse> lines) {
        return lines.stream()
                .map(line -> new OrderLineInput(line.productId(), line.qty(), line.note()))
                .toList();
    }

    private List<SaleOrderLineResponse> queryOrderLines(Long saleOrderId) {
        return jdbcTemplate.query("""
                SELECT line_number, product_id, product_code, product_name_snapshot, unit_price, qty, discount_amount, tax_amount, line_total, note
                FROM pos.sale_order_line
                WHERE sale_order_id = :saleOrderId
                ORDER BY line_number
                """, params("saleOrderId", saleOrderId), (rs, rowNum) -> new SaleOrderLineResponse(
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

    private SaleOrderResponse mapOrder(OrderRecord order) {
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
                queryOrderLines(order.id()),
                queryPayments(order.id())
        );
    }

    private SessionRecord requireSession(Long id) {
        SessionRecord record = jdbcTemplate.query("""
                SELECT id, session_code, region_id, outlet_id, terminal_id, currency_code, cashier_user_id, manager_user_id, business_date,
                       status, note, opened_at, closed_at, reconciled_at, expected_cash_amount, counted_cash_amount, discrepancy_amount
                FROM pos.pos_session
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new SessionRecord(
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
                instant(rs, "opened_at"),
                instant(rs, "closed_at"),
                instant(rs, "reconciled_at"),
                rs.getBigDecimal("expected_cash_amount"),
                rs.getBigDecimal("counted_cash_amount"),
                rs.getBigDecimal("discrepancy_amount")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("POS session not found");
        }
        return record;
    }

    private OrderRecord requireOrder(Long id) {
        OrderRecord record = jdbcTemplate.query("""
                SELECT id, order_number, region_id, outlet_id, pos_session_id, currency_code, order_type, status, payment_status,
                       subtotal, discount_amount, tax_amount, total_amount, note, created_at, completed_at
                FROM pos.sale_order
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new OrderRecord(
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
                instant(rs, "created_at"),
                instant(rs, "completed_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Sale order not found");
        }
        return record;
    }

    private PosSessionResponse mapSession(SessionRecord session) {
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

    private void ensureSessionOpen(SessionRecord session) {
        if (!"OPEN".equals(session.status())) {
            throw new ConflictException("The POS session is not open");
        }
    }

    private void ensureOrderOpen(OrderRecord order) {
        if (!"OPEN".equals(order.status())) {
            throw new ConflictException("Only open orders can be modified");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }
    }

    private Long insertForId(String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"id"});
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

    private String currentTerminalIdFilter() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        String terminalId = attributes.getRequest().getParameter("terminalId");
        return terminalId == null || terminalId.isBlank() ? null : terminalId;
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
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

    private record SessionRecord(
            Long id,
            String sessionCode,
            Long regionId,
            Long outletId,
            String terminalId,
            String currencyCode,
            Long cashierUserId,
            Long managerUserId,
            LocalDate businessDate,
            String status,
            String note,
            Instant openedAt,
            Instant closedAt,
            Instant reconciledAt,
            BigDecimal expectedCashAmount,
            BigDecimal countedCashAmount,
            BigDecimal discrepancyAmount
    ) {
    }

    private record OrderRecord(
            Long id,
            String orderNumber,
            Long regionId,
            Long outletId,
            Long posSessionId,
            String currencyCode,
            String orderType,
            String status,
            String paymentStatus,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String note,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    private record PricedLine(
            Integer lineNumber,
            Long productId,
            String productCode,
            String productNameSnapshot,
            BigDecimal unitPrice,
            BigDecimal qty,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String note
    ) {
    }

    private record PricingSnapshot(
            List<PricedLine> lines,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount
    ) {
    }

    private record MenuResponse(List<MenuItem> items) {
    }

    private record MenuItem(
            Long productId,
            String productCode,
            String productName,
            String categoryCode,
            String currencyCode,
            BigDecimal priceValue,
            BigDecimal taxPercent
    ) {
    }

    private record RecipeSnapshot(
            Long productId,
            Long recipeId,
            Long recipeVersionId,
            String recipeCode,
            String versionNo,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            List<RecipeIngredient> ingredients
    ) {
    }

    private record RecipeIngredient(
            Long ingredientId,
            String ingredientCode,
            String ingredientName,
            String uomCode,
            BigDecimal qty,
            Integer sortOrder
    ) {
    }
}
