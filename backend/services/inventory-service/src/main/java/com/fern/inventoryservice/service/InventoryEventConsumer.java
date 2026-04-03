package com.fern.inventoryservice.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {
    private final InventoryEventConsumerService inventoryEventConsumerService;

    public InventoryEventConsumer(InventoryEventConsumerService inventoryEventConsumerService) {
        this.inventoryEventConsumerService = inventoryEventConsumerService;
    }

    @KafkaListener(topics = "pos.sale.completed", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeSaleCompleted(String payload) throws Exception {
        inventoryEventConsumerService.consumeSaleCompleted(payload);
    }

    @KafkaListener(topics = "procurement.goods_receipt.posted", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeGoodsReceiptPosted(String payload) throws Exception {
        inventoryEventConsumerService.consumeGoodsReceiptPosted(payload);
    }
}
