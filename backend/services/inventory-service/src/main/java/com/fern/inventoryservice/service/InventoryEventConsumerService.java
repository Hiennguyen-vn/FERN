package com.fern.inventoryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import java.time.Clock;
import java.util.Comparator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class InventoryEventConsumerService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final InventoryAuthorizer inventoryAuthorizer;
    private final InventoryRepository inventoryRepository;
    private final StockReservationService stockReservationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionOperations transactionOperations;

    public InventoryEventConsumerService(
            NamedParameterJdbcTemplate jdbcTemplate,
            InventoryAuthorizer inventoryAuthorizer,
            InventoryRepository inventoryRepository,
            StockReservationService stockReservationService,
            ObjectMapper objectMapper,
            Clock clock,
            TransactionOperations transactionOperations
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryAuthorizer = inventoryAuthorizer;
        this.inventoryRepository = inventoryRepository;
        this.stockReservationService = stockReservationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionOperations = transactionOperations;
    }

    public void consumeSaleCompleted(PosSaleCompletedEvent event) {
        if (!transactionOperations.execute(status ->
                inventoryRepository.beginInbox(event.eventId(), "pos-service", event.eventType(), event.saleOrderId().toString(), event))) {
            return;
        }
        try {
            transactionOperations.executeWithoutResult(status -> {
                stockReservationService.commitSaleCompletion(event);
                inventoryRepository.markInboxProcessed(event.eventId());
            });
        } catch (RuntimeException exception) {
            transactionOperations.executeWithoutResult(status ->
                    inventoryRepository.markInboxFailed(event.eventId(), exception));
            throw exception;
        }
    }

    public void consumeGoodsReceiptPosted(ProcurementGoodsReceiptPostedEvent event) {
        if (!transactionOperations.execute(status ->
                inventoryRepository.beginInbox(event.eventId(), "procurement-service", event.eventType(), event.goodsReceiptId().toString(), event))) {
            return;
        }
        try {
            transactionOperations.executeWithoutResult(status -> {
                event.lines().stream()
                        .map(GoodsReceiptPostedLine::ingredientId)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .forEach(ingredientId -> inventoryRepository.lockStockBalance(
                                event.regionId(),
                                event.outletId(),
                                ingredientId
                        ));
                for (GoodsReceiptPostedLine line : event.lines()) {
                    inventoryRepository.appendTransaction(
                            event.regionId(),
                            event.outletId(),
                            line.ingredientId(),
                            line.qtyReceived(),
                            event.businessDate(),
                            InventoryTxnType.PURCHASE_IN.name(),
                            line.unitCost(),
                            "GOODS_RECEIPT",
                            event.goodsReceiptId().toString(),
                            event.postedByUserId()
                    );
                    inventoryRepository.applyBalanceDelta(event.regionId(), event.outletId(), line.ingredientId(), line.qtyReceived(), line.unitCost(), false);
                }
                inventoryRepository.markInboxProcessed(event.eventId());
            });
        } catch (RuntimeException exception) {
            transactionOperations.executeWithoutResult(status ->
                    inventoryRepository.markInboxFailed(event.eventId(), exception));
            throw exception;
        }
    }
}
