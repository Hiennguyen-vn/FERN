package com.fern.reportservice.service;

import com.fern.platform.contracts.SupplierInvoiceApprovedEvent;
import com.fern.platform.contracts.SupplierPaymentAllocation;
import com.fern.platform.contracts.SupplierPaymentRecordedEvent;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PayablesEventProjector {
    private static final List<String> PAYABLES_DATASETS = List.of("payables_fact");

    private final ReportIngestionSupport support;

    public PayablesEventProjector(ReportIngestionSupport support) {
        this.support = support;
    }

    public void ingestSupplierInvoiceApproved(String payload, SupplierInvoiceApprovedEvent event) {
        support.ingestWithLanding(
                PAYABLES_DATASETS,
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "procurement.supplier_invoice.approved",
                payload,
                () -> {
                    support.jdbcTemplate().update("""
                            INSERT INTO report.payables_fact (
                                fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                region_id, outlet_id, supplier_id, supplier_invoice_id, supplier_payment_id, business_date, currency_code,
                                fact_type, amount, tax_amount, matched_receipt_amount, variance_amount, payload
                            ) VALUES (
                                :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                :regionId, :outletId, :supplierId, :supplierInvoiceId, NULL, :businessDate, :currencyCode,
                                :factType, :amount, :taxAmount, :matchedReceiptAmount, :varianceAmount, CAST(:payload AS jsonb)
                            )
                            ON CONFLICT (idempotency_key) DO NOTHING
                            """, support.params(
                            "factId", support.idGenerator().nextId(),
                            "sourceEventId", event.eventId(),
                            "sourceService", event.sourceService(),
                            "eventType", event.eventType(),
                            "occurredAt", event.occurredAt(),
                            "idempotencyKey", event.idempotencyKey(),
                            "regionId", event.regionId(),
                            "outletId", event.outletId(),
                            "supplierId", event.supplierId(),
                            "supplierInvoiceId", event.supplierInvoiceId(),
                            "businessDate", event.invoiceDate(),
                            "currencyCode", event.currencyCode(),
                            "factType", "SUPPLIER_INVOICE_APPROVED",
                            "amount", event.totalAmount(),
                            "taxAmount", event.taxAmount(),
                            "matchedReceiptAmount", event.matchedReceiptAmount(),
                            "varianceAmount", event.varianceAmount(),
                            "payload", payload
                    ));
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }

    public void ingestSupplierPaymentRecorded(String payload, SupplierPaymentRecordedEvent event) {
        support.ingestWithLanding(
                PAYABLES_DATASETS,
                event.eventId(),
                event.sourceService(),
                event.eventType(),
                event.occurredAt(),
                event.idempotencyKey(),
                "procurement.supplier.payment.recorded",
                payload,
                () -> {
                    for (SupplierPaymentAllocation allocation : event.invoiceAllocations()) {
                        support.jdbcTemplate().update("""
                                INSERT INTO report.payables_fact (
                                    fact_id, source_event_id, source_service, event_type, occurred_at, ingested_at, idempotency_key,
                                    region_id, outlet_id, supplier_id, supplier_invoice_id, supplier_payment_id, business_date, currency_code,
                                    fact_type, amount, tax_amount, matched_receipt_amount, variance_amount, payload
                                ) VALUES (
                                    :factId, :sourceEventId, :sourceService, :eventType, :occurredAt, CURRENT_TIMESTAMP, :idempotencyKey,
                                    NULL, NULL, :supplierId, :supplierInvoiceId, :supplierPaymentId, :businessDate, :currencyCode,
                                    :factType, :amount, NULL, NULL, NULL, CAST(:payload AS jsonb)
                                )
                                ON CONFLICT (idempotency_key) DO NOTHING
                                """, support.params(
                                "factId", support.idGenerator().nextId(),
                                "sourceEventId", event.eventId(),
                                "sourceService", event.sourceService(),
                                "eventType", event.eventType(),
                                "occurredAt", event.occurredAt(),
                                "idempotencyKey", event.idempotencyKey() + ":allocation:" + allocation.supplierInvoiceId(),
                                "supplierId", event.supplierId(),
                                "supplierInvoiceId", allocation.supplierInvoiceId(),
                                "supplierPaymentId", event.paymentId(),
                                "businessDate", java.time.LocalDate.ofInstant(event.paymentTime(), java.time.ZoneOffset.UTC),
                                "currencyCode", event.currencyCode(),
                                "factType", "SUPPLIER_PAYMENT_ALLOCATION",
                                "amount", allocation.allocatedAmount(),
                                "payload", support.toJson(allocation)
                        ));
                    }
                    support.updateProjectionLag(event.occurredAt());
                }
        );
    }
}
