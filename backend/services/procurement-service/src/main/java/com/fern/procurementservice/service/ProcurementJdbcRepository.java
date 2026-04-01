package com.fern.procurementservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.procurementservice.dto.ProcurementCommands.GoodsReceiptLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.PurchaseOrderLineInput;
import com.fern.procurementservice.dto.ProcurementCommands.SupplierInvoiceLineInput;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptLineResponse;
import com.fern.procurementservice.dto.ProcurementResponses.GoodsReceiptResponse;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderLineResponse;
import com.fern.procurementservice.dto.ProcurementResponses.PurchaseOrderResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceLineResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierInvoiceResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentAllocationResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierPaymentResponse;
import com.fern.procurementservice.dto.ProcurementResponses.SupplierResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class ProcurementJdbcRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final ObjectMapper objectMapper;

    ProcurementJdbcRepository(
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    NamedParameterJdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    NamedParameterJdbcTemplate masterJdbcTemplate() {
        return masterJdbcTemplate;
    }

    SupplierResponse getSupplier(Long id) {
        SupplierResponse response = masterJdbcTemplate.query("""
                SELECT id, supplier_code, name, tax_code, email, phone, address, default_region_id, status, approved_at
                FROM procurement_master.supplier
                WHERE id = :id AND deleted_at IS NULL
                """, params("id", id), rs -> rs.next() ? new SupplierResponse(
                rs.getLong("id"),
                rs.getString("supplier_code"),
                rs.getString("name"),
                rs.getString("tax_code"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getObject("default_region_id", Long.class),
                rs.getString("status"),
                instant(rs, "approved_at")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Supplier not found");
        }
        return response;
    }

    SupplierRecord requireSupplier(Long id) {
        SupplierResponse response = getSupplier(id);
        return new SupplierRecord(response.id(), response.status(), response.approvedAt());
    }

    SupplierRecord requireActiveApprovedSupplier(Long id) {
        SupplierRecord supplier = requireSupplier(id);
        if (!"ACTIVE".equals(supplier.status()) || supplier.approvedAt() == null) {
            throw new ConflictException("Supplier must be active and approved before it can be referenced");
        }
        return supplier;
    }

    PurchaseOrderRecord requirePurchaseOrder(Long id) {
        PurchaseOrderRecord record = jdbcTemplate.query("""
                SELECT id, po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status, subtotal_amount, tax_amount, total_amount, note, approved_at, issued_at
                FROM procurement.purchase_order
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new PurchaseOrderRecord(
                rs.getLong("id"),
                rs.getString("po_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                instant(rs, "approved_at"),
                instant(rs, "issued_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Purchase order not found");
        }
        return record;
    }

    PurchaseOrderRecord requirePurchaseOrderForUpdate(Long id) {
        PurchaseOrderRecord record = jdbcTemplate.query("""
                SELECT id, po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status, subtotal_amount, tax_amount, total_amount, note, approved_at, issued_at
                FROM procurement.purchase_order
                WHERE id = :id
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? new PurchaseOrderRecord(
                rs.getLong("id"),
                rs.getString("po_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                instant(rs, "approved_at"),
                instant(rs, "issued_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Purchase order not found");
        }
        return record;
    }

    GoodsReceiptRecord requireGoodsReceipt(Long id) {
        GoodsReceiptRecord record = jdbcTemplate.query("""
                SELECT id, receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                       status, total_amount, supplier_lot_number, note, received_at, posted_at
                FROM procurement.goods_receipt
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new GoodsReceiptRecord(
                rs.getLong("id"),
                rs.getString("receipt_number"),
                rs.getLong("purchase_order_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                instant(rs, "receipt_time"),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("supplier_lot_number"),
                rs.getString("note"),
                instant(rs, "received_at"),
                instant(rs, "posted_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Goods receipt not found");
        }
        return record;
    }

    GoodsReceiptRecord requireGoodsReceiptForUpdate(Long id) {
        GoodsReceiptRecord record = jdbcTemplate.query("""
                SELECT id, receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                       status, total_amount, supplier_lot_number, note, received_at, posted_at
                FROM procurement.goods_receipt
                WHERE id = :id
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? new GoodsReceiptRecord(
                rs.getLong("id"),
                rs.getString("receipt_number"),
                rs.getLong("purchase_order_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                instant(rs, "receipt_time"),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("supplier_lot_number"),
                rs.getString("note"),
                instant(rs, "received_at"),
                instant(rs, "posted_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Goods receipt not found");
        }
        return record;
    }

    SupplierInvoiceRecord requireSupplierInvoice(Long id) {
        SupplierInvoiceRecord record = jdbcTemplate.query("""
                SELECT id, supplier_id, region_id, outlet_id, currency_code, invoice_number, invoice_date, due_date,
                       subtotal, tax_amount, total_amount, status, note, approved_at
                FROM procurement.supplier_invoice
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new SupplierInvoiceRecord(
                rs.getLong("id"),
                rs.getLong("supplier_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("currency_code"),
                rs.getString("invoice_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getString("note"),
                instant(rs, "approved_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Supplier invoice not found");
        }
        return record;
    }

    SupplierInvoiceRecord requireSupplierInvoiceForUpdate(Long id) {
        SupplierInvoiceRecord record = jdbcTemplate.query("""
                SELECT id, supplier_id, region_id, outlet_id, currency_code, invoice_number, invoice_date, due_date,
                       subtotal, tax_amount, total_amount, status, note, approved_at
                FROM procurement.supplier_invoice
                WHERE id = :id
                FOR UPDATE
                """, params("id", id), rs -> rs.next() ? new SupplierInvoiceRecord(
                rs.getLong("id"),
                rs.getLong("supplier_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("currency_code"),
                rs.getString("invoice_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getString("note"),
                instant(rs, "approved_at")
        ) : null);
        if (record == null) {
            throw new ResourceNotFoundException("Supplier invoice not found");
        }
        return record;
    }

    BigDecimal invoiceAllocatedAmount(Long supplierInvoiceId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocated_amount), 0)
                FROM procurement.supplier_payment_allocation
                WHERE supplier_invoice_id = :supplierInvoiceId
                """, params("supplierInvoiceId", supplierInvoiceId), BigDecimal.class);
    }

    PurchaseOrderResponse mapPurchaseOrder(PurchaseOrderRecord record) {
        List<PurchaseOrderLineResponse> lines = jdbcTemplate.query("""
                SELECT id, line_number, ingredient_id, uom_code, qty_ordered, qty_received, expected_unit_price, tax_percent, status, note
                FROM procurement.purchase_order_line
                WHERE purchase_order_id = :purchaseOrderId
                ORDER BY line_number
                """, params("purchaseOrderId", record.id()), (rs, rowNum) -> new PurchaseOrderLineResponse(
                rs.getLong("id"),
                rs.getInt("line_number"),
                rs.getLong("ingredient_id"),
                rs.getString("uom_code"),
                rs.getBigDecimal("qty_ordered"),
                rs.getBigDecimal("qty_received"),
                rs.getBigDecimal("expected_unit_price"),
                rs.getBigDecimal("tax_percent"),
                rs.getString("status"),
                rs.getString("note")
        ));
        return new PurchaseOrderResponse(
                record.id(),
                record.poNumber(),
                record.regionId(),
                record.outletId(),
                record.supplierId(),
                record.orderDate(),
                record.expectedDeliveryDate(),
                record.status(),
                record.subtotalAmount(),
                record.taxAmount(),
                record.totalAmount(),
                record.note(),
                record.approvedAt(),
                record.issuedAt(),
                lines
        );
    }

    GoodsReceiptResponse mapGoodsReceipt(GoodsReceiptRecord record) {
        List<GoodsReceiptLineResponse> lines = jdbcTemplate.query("""
                SELECT id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost, line_total, note
                FROM procurement.goods_receipt_line
                WHERE goods_receipt_id = :goodsReceiptId
                ORDER BY id
                """, params("goodsReceiptId", record.id()), (rs, rowNum) -> new GoodsReceiptLineResponse(
                rs.getLong("id"),
                rs.getObject("purchase_order_line_id", Long.class),
                rs.getLong("ingredient_id"),
                rs.getString("uom_code"),
                rs.getBigDecimal("qty_received"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("line_total"),
                rs.getString("note")
        ));
        return new GoodsReceiptResponse(
                record.id(),
                record.receiptNumber(),
                record.purchaseOrderId(),
                record.regionId(),
                record.outletId(),
                record.supplierId(),
                record.receiptTime(),
                record.businessDate(),
                record.status(),
                record.totalAmount(),
                record.supplierLotNumber(),
                record.note(),
                record.receivedAt(),
                record.postedAt(),
                lines
        );
    }

    SupplierInvoiceResponse mapSupplierInvoice(SupplierInvoiceRecord record) {
        List<SupplierInvoiceLineResponse> lines = jdbcTemplate.query("""
                SELECT id, line_number, line_type, goods_receipt_line_id, description, qty_invoiced, unit_price, tax_percent, tax_amount, line_total, note
                FROM procurement.supplier_invoice_line
                WHERE supplier_invoice_id = :supplierInvoiceId
                ORDER BY line_number
                """, params("supplierInvoiceId", record.id()), (rs, rowNum) -> new SupplierInvoiceLineResponse(
                rs.getLong("id"),
                rs.getInt("line_number"),
                rs.getString("line_type"),
                rs.getObject("goods_receipt_line_id", Long.class),
                rs.getString("description"),
                rs.getBigDecimal("qty_invoiced"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("tax_percent"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("line_total"),
                rs.getString("note")
        ));
        return new SupplierInvoiceResponse(
                record.id(),
                record.supplierId(),
                record.regionId(),
                record.outletId(),
                record.currencyCode(),
                record.invoiceNumber(),
                record.invoiceDate(),
                record.dueDate(),
                record.subtotal(),
                record.taxAmount(),
                record.totalAmount(),
                record.status(),
                record.note(),
                record.approvedAt(),
                lines
        );
    }

    SupplierPaymentResponse getSupplierPayment(Long id) {
        SupplierPaymentResponse response = jdbcTemplate.query("""
                SELECT id, payment_number, supplier_id, currency_code, payment_method, amount, payment_time, transaction_ref, note
                FROM procurement.supplier_payment
                WHERE id = :id
                """, params("id", id), rs -> rs.next() ? new SupplierPaymentResponse(
                rs.getLong("id"),
                rs.getString("payment_number"),
                rs.getLong("supplier_id"),
                rs.getString("currency_code"),
                rs.getString("payment_method"),
                rs.getBigDecimal("amount"),
                instant(rs, "payment_time"),
                rs.getString("transaction_ref"),
                rs.getString("note"),
                jdbcTemplate.query("""
                        SELECT supplier_invoice_id, allocated_amount, note
                        FROM procurement.supplier_payment_allocation
                        WHERE supplier_payment_id = :supplierPaymentId
                        ORDER BY supplier_invoice_id
                        """, params("supplierPaymentId", id), (allocationRs, rowNum) -> new SupplierPaymentAllocationResponse(
                        allocationRs.getLong("supplier_invoice_id"),
                        allocationRs.getBigDecimal("allocated_amount"),
                        allocationRs.getString("note")
                ))
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Supplier payment not found");
        }
        return response;
    }

    List<Long> listSupplierPaymentIds(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM procurement.supplier_payment
                ORDER BY payment_time DESC, id DESC
                LIMIT :limit
                """, params("limit", limit), Long.class);
    }

    List<Long> listSupplierPaymentIds(Long supplierId, int limit) {
        if (supplierId == null) {
            return listSupplierPaymentIds(limit);
        }
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM procurement.supplier_payment
                WHERE supplier_id = :supplierId
                ORDER BY payment_time DESC, id DESC
                LIMIT :limit
                """, params("supplierId", supplierId, "limit", limit), Long.class);
    }

    List<PurchaseOrderRecord> listPurchaseOrders(Long outletId, Long supplierId, String status, int limit) {
        MapSqlParameterSource p = params("limit", limit);
        StringBuilder sql = new StringBuilder("""
                SELECT id, po_number, region_id, outlet_id, supplier_id, order_date, expected_delivery_date, status, subtotal_amount, tax_amount, total_amount, note, approved_at, issued_at
                FROM procurement.purchase_order
                WHERE 1=1
                """);
        if (outletId != null) {
            sql.append(" AND outlet_id = :outletId");
            p.addValue("outletId", outletId);
        }
        if (supplierId != null) {
            sql.append(" AND supplier_id = :supplierId");
            p.addValue("supplierId", supplierId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            p.addValue("status", status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit");
        return jdbcTemplate.query(sql.toString(), p, (rs, rowNum) -> new PurchaseOrderRecord(
                rs.getLong("id"),
                rs.getString("po_number"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("note"),
                instant(rs, "approved_at"),
                instant(rs, "issued_at")
        ));
    }

    List<GoodsReceiptRecord> listGoodsReceipts(Long purchaseOrderId, Long outletId, String status, int limit) {
        MapSqlParameterSource p = params("limit", limit);
        StringBuilder sql = new StringBuilder("""
                SELECT id, receipt_number, purchase_order_id, region_id, outlet_id, supplier_id, receipt_time, business_date,
                       status, total_amount, supplier_lot_number, note, received_at, posted_at
                FROM procurement.goods_receipt
                WHERE 1=1
                """);
        if (purchaseOrderId != null) {
            sql.append(" AND purchase_order_id = :purchaseOrderId");
            p.addValue("purchaseOrderId", purchaseOrderId);
        }
        if (outletId != null) {
            sql.append(" AND outlet_id = :outletId");
            p.addValue("outletId", outletId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            p.addValue("status", status);
        }
        sql.append(" ORDER BY receipt_time DESC LIMIT :limit");
        return jdbcTemplate.query(sql.toString(), p, (rs, rowNum) -> new GoodsReceiptRecord(
                rs.getLong("id"),
                rs.getString("receipt_number"),
                rs.getLong("purchase_order_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getLong("supplier_id"),
                instant(rs, "receipt_time"),
                rs.getObject("business_date", LocalDate.class),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getString("supplier_lot_number"),
                rs.getString("note"),
                instant(rs, "received_at"),
                instant(rs, "posted_at")
        ));
    }

    List<SupplierInvoiceRecord> listSupplierInvoices(Long supplierId, Long outletId, String status, int limit) {
        MapSqlParameterSource p = params("limit", limit);
        StringBuilder sql = new StringBuilder("""
                SELECT id, supplier_id, region_id, outlet_id, currency_code, invoice_number, invoice_date, due_date,
                       subtotal, tax_amount, total_amount, status, note, approved_at
                FROM procurement.supplier_invoice
                WHERE 1=1
                """);
        if (supplierId != null) {
            sql.append(" AND supplier_id = :supplierId");
            p.addValue("supplierId", supplierId);
        }
        if (outletId != null) {
            sql.append(" AND outlet_id = :outletId");
            p.addValue("outletId", outletId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status");
            p.addValue("status", status);
        }
        sql.append(" ORDER BY invoice_date DESC, id DESC LIMIT :limit");
        return jdbcTemplate.query(sql.toString(), p, (rs, rowNum) -> new SupplierInvoiceRecord(
                rs.getLong("id"),
                rs.getLong("supplier_id"),
                rs.getLong("region_id"),
                rs.getLong("outlet_id"),
                rs.getString("currency_code"),
                rs.getString("invoice_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getString("note"),
                instant(rs, "approved_at")
        ));
    }

    void replacePurchaseOrderLines(Long purchaseOrderId, List<PurchaseOrderLineInput> lines) {
        jdbcTemplate.update("DELETE FROM procurement.purchase_order_line WHERE purchase_order_id = :purchaseOrderId", params("purchaseOrderId", purchaseOrderId));
        int lineNumber = 1;
        for (PurchaseOrderLineInput line : lines) {
            jdbcTemplate.update("""
                    INSERT INTO procurement.purchase_order_line (
                        purchase_order_id, line_number, ingredient_id, uom_code, qty_ordered, qty_received,
                        expected_unit_price, tax_percent, status, note, created_at, updated_at
                    ) VALUES (
                        :purchaseOrderId, :lineNumber, :ingredientId, :uomCode, :qtyOrdered, 0,
                        :expectedUnitPrice, :taxPercent, 'OPEN', :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "purchaseOrderId", purchaseOrderId,
                    "lineNumber", lineNumber++,
                    "ingredientId", line.ingredientId(),
                    "uomCode", line.uomCode(),
                    "qtyOrdered", line.qtyOrdered(),
                    "expectedUnitPrice", line.expectedUnitPrice(),
                    "taxPercent", line.taxPercent() == null ? BigDecimal.ZERO : line.taxPercent(),
                    "note", line.note()
            ));
        }
    }

    void insertGoodsReceiptLines(Long goodsReceiptId, List<GoodsReceiptLineInput> lines) {
        for (GoodsReceiptLineInput line : lines) {
            BigDecimal lineTotal = line.qtyReceived().multiply(line.unitCost()).setScale(2, RoundingMode.HALF_UP);
            jdbcTemplate.update("""
                    INSERT INTO procurement.goods_receipt_line (
                        goods_receipt_id, purchase_order_line_id, ingredient_id, uom_code, qty_received, unit_cost,
                        line_total, manufacture_date, expiry_date, note, created_at, updated_at
                    ) VALUES (
                        :goodsReceiptId, :purchaseOrderLineId, :ingredientId, :uomCode, :qtyReceived, :unitCost,
                        :lineTotal, :manufactureDate, :expiryDate, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "goodsReceiptId", goodsReceiptId,
                    "purchaseOrderLineId", line.purchaseOrderLineId(),
                    "ingredientId", line.ingredientId(),
                    "uomCode", line.uomCode(),
                    "qtyReceived", line.qtyReceived(),
                    "unitCost", line.unitCost(),
                    "lineTotal", lineTotal,
                    "manufactureDate", line.manufactureDate(),
                    "expiryDate", line.expiryDate(),
                    "note", line.note()
            ));
        }
    }

    void insertInvoiceLines(Long supplierInvoiceId, List<SupplierInvoiceLineInput> lines) {
        int lineNumber = 1;
        for (SupplierInvoiceLineInput line : lines) {
            jdbcTemplate.update("""
                    INSERT INTO procurement.supplier_invoice_line (
                        supplier_invoice_id, line_number, line_type, goods_receipt_line_id, description, qty_invoiced,
                        unit_price, tax_percent, tax_amount, line_total, note, created_at, updated_at
                    ) VALUES (
                        :supplierInvoiceId, :lineNumber, :lineType, :goodsReceiptLineId, :description, :qtyInvoiced,
                        :unitPrice, :taxPercent, :taxAmount, :lineTotal, :note, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """, params(
                    "supplierInvoiceId", supplierInvoiceId,
                    "lineNumber", lineNumber++,
                    "lineType", line.lineType(),
                    "goodsReceiptLineId", line.goodsReceiptLineId(),
                    "description", line.description(),
                    "qtyInvoiced", line.qtyInvoiced(),
                    "unitPrice", line.unitPrice(),
                    "taxPercent", line.taxPercent() == null ? BigDecimal.ZERO : line.taxPercent(),
                    "taxAmount", line.taxAmount() == null ? BigDecimal.ZERO : line.taxAmount(),
                    "lineTotal", line.lineTotal(),
                    "note", line.note()
            ));
        }
    }

    void updatePurchaseOrderReceiptProgress(Long purchaseOrderId, Long goodsReceiptId) {
        List<GoodsReceiptLineResponse> receiptLines = mapGoodsReceipt(requireGoodsReceipt(goodsReceiptId)).lines();
        for (GoodsReceiptLineResponse receiptLine : receiptLines) {
            if (receiptLine.purchaseOrderLineId() == null) {
                continue;
            }
            jdbcTemplate.update("""
                    UPDATE procurement.purchase_order_line
                    SET qty_received = qty_received + :qtyReceived,
                        status = CASE
                            WHEN qty_received + :qtyReceived >= qty_ordered THEN 'COMPLETED'
                            ELSE 'PARTIALLY_RECEIVED'
                        END,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :purchaseOrderLineId
                    """, params(
                    "qtyReceived", receiptLine.qtyReceived(),
                    "purchaseOrderLineId", receiptLine.purchaseOrderLineId()
            ));
        }
        List<String> lineStatuses = jdbcTemplate.queryForList("""
                SELECT status
                FROM procurement.purchase_order_line
                WHERE purchase_order_id = :purchaseOrderId
                """, params("purchaseOrderId", purchaseOrderId), String.class);
        String headerStatus = lineStatuses.stream().allMatch("COMPLETED"::equals) ? "COMPLETED" : "PARTIALLY_RECEIVED";
        jdbcTemplate.update("""
                UPDATE procurement.purchase_order
                SET status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, params("status", headerStatus, "id", purchaseOrderId));
    }

    Long insertForId(NamedParameterJdbcTemplate template, String sql, MapSqlParameterSource parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(sql, parameters, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return generated id");
        }
        return key.longValue();
    }

    MapSqlParameterSource params(Object... values) {
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

    String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize payload", exception);
        }
    }

    Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
