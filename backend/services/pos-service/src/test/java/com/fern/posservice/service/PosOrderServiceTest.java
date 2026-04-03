package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.posservice.dto.PosCommands.AddPaymentRequest;
import com.fern.posservice.dto.PosResponses.SaleOrderResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class PosOrderServiceTest {
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private PosAuthorizer posAuthorizer;

    @Mock
    private PosStore store;

    @Mock
    private PosOrgClient posOrgClient;

    @Mock
    private PosPricingService pricingService;

    @Mock
    private PosInventoryClient inventoryClient;

    @Mock
    private PosReferenceCodeGenerator codeGenerator;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private OperationalAlertPublisher operationalAlertPublisher;

    @Mock
    private PosAuditService posAuditService;

    private PosOrderService service;
    private FernPrincipal principal;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mockTransactionStatus());
        }).when(transactionTemplate).execute(any());
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(mockTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new PosOrderService(
                jdbcTemplate,
                posAuthorizer,
                store,
                posOrgClient,
                pricingService,
                inventoryClient,
                codeGenerator,
                transactionTemplate,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-03-29T08:00:00Z"), ZoneOffset.UTC),
                operationalAlertPublisher,
                posAuditService,
                new SimpleMeterRegistry()
        );
        principal = new FernPrincipal(
                99L,
                "cashier",
                Set.of("CASHIER"),
                Set.of(
                        PermissionCodes.POS_ORDER_COMPLETE,
                        PermissionCodes.POS_ORDER_CANCEL,
                        PermissionCodes.POS_ORDER_UPDATE,
                        PermissionCodes.POS_ORDER_READ
                ),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "jti-1"
        );
    }

    @Test
    void shouldReturnCurrentOrderWhenCompletionIsReplayed() {
        OrderRecord completedOrder = order(SaleOrderStatus.COMPLETED.name(), new BigDecimal("10.00"), 555L);
        SaleOrderResponse response = response(completedOrder);
        when(store.requireOrder(10L)).thenReturn(completedOrder, completedOrder);
        when(store.mapOrder(completedOrder)).thenReturn(response);

        SaleOrderResponse result = service.completeOrder(principal, 10L, "corr-1");

        verifyNoInteractions(inventoryClient);
        verify(transactionTemplate, never()).execute(any());
        assertThat(result).isSameAs(response);
    }

    @Test
    void shouldRejectCompletionWhileAnotherCompletionIsInProgress() {
        OrderRecord completingOrder = order(SaleOrderStatus.COMPLETING.name(), new BigDecimal("10.00"), null);
        when(store.requireOrder(10L)).thenReturn(completingOrder);

        assertThatThrownBy(() -> service.completeOrder(principal, 10L, "corr-2"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Order completion is already in progress");

        verifyNoInteractions(inventoryClient);
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void shouldRejectCompletionUntilSuccessfulPaymentsCoverOrderTotal() {
        OrderRecord openOrder = order(SaleOrderStatus.OPEN.name(), new BigDecimal("10.00"), null);
        when(store.requireOrder(10L)).thenReturn(openOrder);
        when(store.successfulPaymentTotal(10L)).thenReturn(new BigDecimal("9.99"));

        assertThatThrownBy(() -> service.completeOrder(principal, 10L, "corr-3"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Order cannot be completed until payment covers the full total");

        verifyNoInteractions(inventoryClient);
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void shouldRejectCancellationWhenSuccessfulPaymentsAlreadyExist() {
        OrderRecord openOrder = order(SaleOrderStatus.OPEN.name(), new BigDecimal("10.00"), null);
        when(store.requireOrder(10L)).thenReturn(openOrder);
        when(store.successfulPaymentTotal(10L)).thenReturn(new BigDecimal("1.00"));

        assertThatThrownBy(() -> service.cancelOrder(principal, 10L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Orders with successful payments cannot be cancelled");
    }

    @Test
    void shouldRejectCancellingCompletedOrder() {
        OrderRecord completedOrder = order(SaleOrderStatus.COMPLETED.name(), new BigDecimal("10.00"), 555L);
        when(store.requireOrder(10L)).thenReturn(completedOrder);

        assertThatThrownBy(() -> service.cancelOrder(principal, 10L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only open orders can be modified");

        verify(store, never()).successfulPaymentTotal(10L);
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    @Test
    void shouldRejectUnsupportedPaymentStatusBeforePersistingPayment() {
        OrderRecord openOrder = order(SaleOrderStatus.OPEN.name(), new BigDecimal("10.00"), null);
        when(store.requireOrder(10L)).thenReturn(openOrder);
        when(store.requireOrderForUpdate(10L)).thenReturn(openOrder);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class))).thenReturn(null);

        assertThatThrownBy(() -> service.addPayment(
                principal,
                10L,
                "payment-key-1",
                "corr-4",
                new AddPaymentRequest("CASH", new BigDecimal("10.00"), Instant.parse("2026-03-29T08:15:00Z"), "TXN-1", "note", "PENDING")
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported payment status");

        verify(store, never()).refreshPaymentStatus(10L);
    }

    private OrderRecord order(String status, BigDecimal totalAmount, Long reservationId) {
        return new OrderRecord(
                10L,
                "SO-10",
                1L,
                2L,
                3L,
                "VND",
                "DINE_IN",
                status,
                SaleOrderPaymentStatus.UNPAID.name(),
                totalAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                totalAmount,
                null,
                Instant.parse("2026-03-29T08:00:00Z"),
                null,
                reservationId
        );
    }

    private SaleOrderResponse response(OrderRecord order) {
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
                List.of(),
                List.of()
        );
    }

    private TransactionStatus mockTransactionStatus() {
        return new TransactionStatus() {
            @Override
            public boolean isNewTransaction() {
                return false;
            }

            @Override
            public boolean hasSavepoint() {
                return false;
            }

            @Override
            public void setRollbackOnly() {
            }

            @Override
            public boolean isRollbackOnly() {
                return false;
            }

            @Override
            public void flush() {
            }

            @Override
            public boolean isCompleted() {
                return false;
            }

            @Override
            public Object createSavepoint() {
                return null;
            }

            @Override
            public void rollbackToSavepoint(Object savepoint) {
            }

            @Override
            public void releaseSavepoint(Object savepoint) {
            }
        };
    }
}
