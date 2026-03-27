package com.fern.inventoryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "pos.sale.completed", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeSaleCompleted(String payload) throws Exception {
        inventoryService.consumeSaleCompleted(objectMapper.readValue(payload, PosSaleCompletedEvent.class));
    }

    @KafkaListener(topics = "procurement.goods_receipt.posted", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeGoodsReceiptPosted(String payload) throws Exception {
        inventoryService.consumeGoodsReceiptPosted(objectMapper.readValue(payload, ProcurementGoodsReceiptPostedEvent.class));
    }
}
