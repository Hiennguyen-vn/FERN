package com.fern.inventoryservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.inventoryservice.service.InventoryEventConsumer;
import com.fern.inventoryservice.service.InventoryEventConsumerService;
import com.fern.platform.contracts.PosSaleCompletedEvent;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.testsupport.KafkaContractFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryEventContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private InventoryEventConsumerService inventoryEventConsumerService;

    @Test
    void shouldDeserializeSharedPosSaleFixtureAndDispatchToInventoryConsumer() throws Exception {
        InventoryEventConsumer consumer = new InventoryEventConsumer(inventoryEventConsumerService);
        String payload = KafkaContractFixtures.load("pos.sale.completed.json");

        consumer.consumeSaleCompleted(payload);

        verify(inventoryEventConsumerService).consumeSaleCompleted(payload);
        PosSaleCompletedEvent event = objectMapper.readValue(payload, PosSaleCompletedEvent.class);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.saleOrderId()).isEqualTo(100L);
        assertThat(event.reservationId()).isEqualTo(999L);
        assertThat(event.payments()).hasSize(2);
        assertThat(event.recipeUsageItems()).hasSize(1);
        assertThat(event.recipeUsageItems().getFirst().qty()).isEqualByComparingTo(new BigDecimal("180.00"));
    }

    @Test
    void shouldDeserializeSharedGoodsReceiptFixtureAndDispatchToInventoryConsumer() throws Exception {
        InventoryEventConsumer consumer = new InventoryEventConsumer(inventoryEventConsumerService);
        String payload = KafkaContractFixtures.load("procurement.goods_receipt.posted.json");

        consumer.consumeGoodsReceiptPosted(payload);

        verify(inventoryEventConsumerService).consumeGoodsReceiptPosted(payload);
        ProcurementGoodsReceiptPostedEvent event = objectMapper.readValue(payload, ProcurementGoodsReceiptPostedEvent.class);
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.goodsReceiptId()).isEqualTo(33L);
        assertThat(event.purchaseOrderId()).isEqualTo(44L);
        assertThat(event.lines()).hasSize(2);
        assertThat(event.lines().get(1).unitCost()).isEqualByComparingTo(new BigDecimal("7.50"));
    }
}
