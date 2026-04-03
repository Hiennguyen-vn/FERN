package com.fern.inventoryservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.GoodsReceiptPostedLine;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class InventoryEventConsumerService {
    private static final String POS_SALE_COMPLETED_TOPIC = "pos.sale.completed";
    private static final String PROCUREMENT_GOODS_RECEIPT_POSTED_TOPIC = "procurement.goods_receipt.posted";
    private static final String INVENTORY_SERVICE = "inventory-service";

    private final InventoryRepository inventoryRepository;
    private final StockReservationService stockReservationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionOperations transactionOperations;

    public InventoryEventConsumerService(
            InventoryRepository inventoryRepository,
            StockReservationService stockReservationService,
            ObjectMapper objectMapper,
            Clock clock,
            TransactionOperations transactionOperations
    ) {
        this.inventoryRepository = inventoryRepository;
        this.stockReservationService = stockReservationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionOperations = transactionOperations;
    }

    public void consumeSaleCompleted(String payload) throws Exception {
        try {
            consumeSaleCompleted(objectMapper.readValue(payload, PosSaleCompletedEvent.class), payload);
        } catch (JsonProcessingException exception) {
            recordDeserializationFailure(POS_SALE_COMPLETED_TOPIC, payload, PosSaleCompletedEvent.class, exception);
            throw exception;
        }
    }

    public void consumeSaleCompleted(PosSaleCompletedEvent event) {
        consumeSaleCompleted(event, toJson(event));
    }

    public void consumeGoodsReceiptPosted(String payload) throws Exception {
        try {
            consumeGoodsReceiptPosted(objectMapper.readValue(payload, ProcurementGoodsReceiptPostedEvent.class), payload);
        } catch (JsonProcessingException exception) {
            recordDeserializationFailure(
                    PROCUREMENT_GOODS_RECEIPT_POSTED_TOPIC,
                    payload,
                    ProcurementGoodsReceiptPostedEvent.class,
                    exception
            );
            throw exception;
        }
    }

    public void consumeGoodsReceiptPosted(ProcurementGoodsReceiptPostedEvent event) {
        consumeGoodsReceiptPosted(event, toJson(event));
    }

    private void consumeSaleCompleted(PosSaleCompletedEvent event, String payload) {
        String sourceEventId = resolveSourceEventId(event.eventId(), POS_SALE_COMPLETED_TOPIC, payload, "validation");
        String sourceService = resolveSourceService(event.sourceService(), "pos-service");
        String eventType = resolveEventType(event.eventType(), POS_SALE_COMPLETED_TOPIC);
        String partitionKey = resolvePartitionKey(event.saleOrderId(), sourceEventId);
        if (!transactionOperations.execute(status ->
                inventoryRepository.beginInboxRaw(sourceEventId, sourceService, eventType, partitionKey, payload))) {
            return;
        }
        try {
            validateSaleCompletedEvent(event);
            transactionOperations.executeWithoutResult(status -> {
                stockReservationService.commitSaleCompletion(event);
                inventoryRepository.markInboxProcessed(sourceEventId);
            });
        } catch (RuntimeException exception) {
            transactionOperations.executeWithoutResult(status ->
                    inventoryRepository.markInboxFailed(sourceEventId, exception));
            throw exception;
        }
    }

    private void consumeGoodsReceiptPosted(ProcurementGoodsReceiptPostedEvent event, String payload) {
        String sourceEventId = resolveSourceEventId(
                event.eventId(),
                PROCUREMENT_GOODS_RECEIPT_POSTED_TOPIC,
                payload,
                "validation"
        );
        String sourceService = resolveSourceService(event.sourceService(), "procurement-service");
        String eventType = resolveEventType(event.eventType(), PROCUREMENT_GOODS_RECEIPT_POSTED_TOPIC);
        String partitionKey = resolvePartitionKey(event.goodsReceiptId(), sourceEventId);
        if (!transactionOperations.execute(status ->
                inventoryRepository.beginInboxRaw(sourceEventId, sourceService, eventType, partitionKey, payload))) {
            return;
        }
        try {
            validateGoodsReceiptPostedEvent(event);
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
                    inventoryRepository.applyBalanceDelta(
                            event.regionId(),
                            event.outletId(),
                            line.ingredientId(),
                            line.qtyReceived(),
                            line.unitCost(),
                            false
                    );
                }
                inventoryRepository.markInboxProcessed(sourceEventId);
            });
        } catch (RuntimeException exception) {
            transactionOperations.executeWithoutResult(status ->
                    inventoryRepository.markInboxFailed(sourceEventId, exception));
            throw exception;
        }
    }

    private void validateSaleCompletedEvent(PosSaleCompletedEvent event) {
        requireNonBlank(event.eventId(), "Sale completed event id is required");
        requireNonBlank(event.eventType(), "Sale completed event type is required");
        requireNonBlank(event.idempotencyKey(), "Sale completed idempotency key is required");
        requireNonNull(event.saleOrderId(), "Sale completed sale order id is required");
        requireNonNull(event.regionId(), "Sale completed region id is required");
        requireNonNull(event.outletId(), "Sale completed outlet id is required");
        requireNonNull(event.businessDate(), "Sale completed business date is required");
        requireNonNull(event.completedAt(), "Sale completed timestamp is required");
        requireNonNull(event.completedByUserId(), "Sale completed user id is required");
        requireNonNull(event.reservationId(), "Sale completed reservation id is required");
    }

    private void validateGoodsReceiptPostedEvent(ProcurementGoodsReceiptPostedEvent event) {
        requireNonBlank(event.eventId(), "Goods receipt event id is required");
        requireNonBlank(event.eventType(), "Goods receipt event type is required");
        requireNonBlank(event.idempotencyKey(), "Goods receipt idempotency key is required");
        requireNonNull(event.goodsReceiptId(), "Goods receipt id is required");
        requireNonNull(event.purchaseOrderId(), "Purchase order id is required");
        requireNonNull(event.regionId(), "Goods receipt region id is required");
        requireNonNull(event.outletId(), "Goods receipt outlet id is required");
        requireNonNull(event.businessDate(), "Goods receipt business date is required");
        requireNonNull(event.postedAt(), "Goods receipt posted time is required");
        requireNonNull(event.postedByUserId(), "Goods receipt posted by user id is required");
        requireNonNull(event.lines(), "Goods receipt lines are required");
        if (event.lines().isEmpty()) {
            throw new IllegalArgumentException("Goods receipt lines are required");
        }
        event.lines().forEach(line -> {
            requireNonNull(line.ingredientId(), "Goods receipt ingredient id is required");
            requirePositive(line.qtyReceived(), "Goods receipt quantity received must be positive");
            requirePositive(line.unitCost(), "Goods receipt unit cost must be positive");
        });
    }

    private void recordDeserializationFailure(
            String topic,
            String payload,
            Class<?> expectedType,
            JsonProcessingException exception
    ) {
        String sourceEventId = syntheticId("deser", topic, payload);
        String wrappedPayload = failurePayload(topic, payload, expectedType.getName(), exception.getClass().getSimpleName());
        boolean claimed = Boolean.TRUE.equals(transactionOperations.execute(status ->
                inventoryRepository.beginInboxRaw(
                        sourceEventId,
                        INVENTORY_SERVICE,
                        topic + ".deserialization_failed",
                        topic,
                        wrappedPayload
                )));
        if (!claimed) {
            return;
        }
        transactionOperations.executeWithoutResult(status ->
                inventoryRepository.markInboxFailed(sourceEventId, exception.getClass().getSimpleName()));
    }

    private String failurePayload(String topic, String rawPayload, String expectedType, String failureType) {
        return toJson(Map.of(
                "topic", topic,
                "expectedType", expectedType,
                "rawPayload", rawPayload,
                "failureType", failureType,
                "failedAt", clock.instant().toString()
        ));
    }

    private String resolveSourceEventId(String eventId, String topic, String payload, String failureKind) {
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        return syntheticId(failureKind, topic, payload);
    }

    private String resolveSourceService(String sourceService, String fallback) {
        return sourceService == null || sourceService.isBlank() ? fallback : sourceService;
    }

    private String resolveEventType(String eventType, String fallback) {
        return eventType == null || eventType.isBlank() ? fallback : eventType;
    }

    private String resolvePartitionKey(Object partitionKey, String fallback) {
        return partitionKey == null ? fallback : partitionKey.toString();
    }

    private String syntheticId(String prefix, String topic, String payload) {
        return prefix + ":" + topic + ":" + stableHash(prefix, topic, payload);
    }

    private String stableHash(String prefix, String topic, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(topic.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize inventory event payload", exception);
        }
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
