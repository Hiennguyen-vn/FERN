package com.fern.financeservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.contracts.ProcurementGoodsReceiptPostedEvent;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import com.fern.platform.testsupport.KafkaContractFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FinanceProcurementEventContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    static java.util.stream.Stream<Arguments> eventPayloads() {
        return java.util.stream.Stream.of(
                Arguments.of("procurement.goods_receipt.posted.json", ProcurementGoodsReceiptPostedEvent.class),
                Arguments.of("procurement.supplier.payment.recorded.json", SupplierPaymentRecordedEvent.class)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventPayloads")
    void shouldDeserializeSharedProcurementFixtures(String fixture, Class<?> type) {
        assertThatNoException().isThrownBy(() -> objectMapper.readValue(KafkaContractFixtures.load(fixture), type));
    }

    @Test
    void shouldPreserveGoodsReceiptPayloadIntegrityForFinanceConsumer() throws Exception {
        ProcurementGoodsReceiptPostedEvent event = objectMapper.readValue(
                KafkaContractFixtures.load("procurement.goods_receipt.posted.json"),
                ProcurementGoodsReceiptPostedEvent.class
        );

        assertThat(event.goodsReceiptId()).isEqualTo(33L);
        assertThat(event.regionId()).isEqualTo(1L);
        assertThat(event.outletId()).isEqualTo(101L);
        assertThat(event.lines()).hasSize(2);
        assertThat(event.lines().getFirst().qtyReceived()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void shouldPreserveSupplierPaymentPayloadIntegrityForFinanceConsumer() throws Exception {
        SupplierPaymentRecordedEvent event = objectMapper.readValue(
                KafkaContractFixtures.load("procurement.supplier.payment.recorded.json"),
                SupplierPaymentRecordedEvent.class
        );

        assertThat(event.paymentId()).isEqualTo(71L);
        assertThat(event.supplierId()).isEqualTo(22L);
        assertThat(event.currencyCode()).isEqualTo("VND");
        assertThat(event.invoiceAllocations()).hasSize(2);
        assertThat(event.invoiceAllocations().getFirst().allocatedAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }
}
