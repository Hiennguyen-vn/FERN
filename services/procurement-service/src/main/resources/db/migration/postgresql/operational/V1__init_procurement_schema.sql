CREATE SCHEMA IF NOT EXISTS procurement;

CREATE TABLE IF NOT EXISTS procurement.purchase_order (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(100) NOT NULL UNIQUE,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    status VARCHAR(30) NOT NULL,
    subtotal_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    note TEXT,
    created_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_po_status_ck CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'ORDERED', 'PARTIALLY_RECEIVED', 'COMPLETED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT procurement_po_dates_ck CHECK (expected_delivery_date IS NULL OR expected_delivery_date >= order_date)
);

CREATE TABLE IF NOT EXISTS procurement.purchase_order_line (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES procurement.purchase_order(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    uom_code VARCHAR(30) NOT NULL,
    qty_ordered NUMERIC(18, 4) NOT NULL,
    qty_received NUMERIC(18, 4) NOT NULL DEFAULT 0,
    expected_unit_price NUMERIC(18, 4),
    tax_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_po_line_uk UNIQUE (purchase_order_id, line_number),
    CONSTRAINT procurement_po_line_status_ck CHECK (status IN ('OPEN', 'PARTIALLY_RECEIVED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT procurement_po_line_qty_ck CHECK (qty_ordered > 0 AND qty_received >= 0),
    CONSTRAINT procurement_po_line_amount_ck CHECK (
        (expected_unit_price IS NULL OR expected_unit_price >= 0) AND
        tax_percent >= 0 AND tax_percent <= 100
    )
);

CREATE TABLE IF NOT EXISTS procurement.goods_receipt (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(100) NOT NULL UNIQUE,
    purchase_order_id BIGINT REFERENCES procurement.purchase_order(id),
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    receipt_time TIMESTAMPTZ NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    supplier_lot_number VARCHAR(50),
    note TEXT,
    created_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_goods_receipt_status_ck CHECK (status IN ('DRAFT', 'RECEIVED', 'POSTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS procurement.goods_receipt_line (
    id BIGSERIAL PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL REFERENCES procurement.goods_receipt(id) ON DELETE CASCADE,
    purchase_order_line_id BIGINT REFERENCES procurement.purchase_order_line(id),
    ingredient_id BIGINT NOT NULL,
    uom_code VARCHAR(30) NOT NULL,
    qty_received NUMERIC(18, 4) NOT NULL,
    unit_cost NUMERIC(18, 4) NOT NULL,
    line_total NUMERIC(18, 2) NOT NULL,
    manufacture_date DATE,
    expiry_date DATE,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_goods_receipt_line_qty_ck CHECK (qty_received > 0 AND unit_cost >= 0 AND line_total >= 0),
    CONSTRAINT procurement_goods_receipt_line_dates_ck CHECK (expiry_date IS NULL OR manufacture_date IS NULL OR expiry_date >= manufacture_date)
);

CREATE TABLE IF NOT EXISTS procurement.supplier_invoice (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(100) NOT NULL,
    supplier_id BIGINT NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    subtotal NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    note TEXT,
    created_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_supplier_invoice_uk UNIQUE (supplier_id, invoice_number),
    CONSTRAINT procurement_supplier_invoice_status_ck CHECK (status IN ('DRAFT', 'RECEIVED', 'MATCHED', 'APPROVED', 'POSTED', 'DISPUTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS procurement.supplier_invoice_line (
    id BIGSERIAL PRIMARY KEY,
    supplier_invoice_id BIGINT NOT NULL REFERENCES procurement.supplier_invoice(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    line_type VARCHAR(30) NOT NULL,
    goods_receipt_line_id BIGINT REFERENCES procurement.goods_receipt_line(id),
    description TEXT,
    qty_invoiced NUMERIC(18, 4),
    unit_price NUMERIC(18, 4),
    tax_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    line_total NUMERIC(18, 2) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_supplier_invoice_line_uk UNIQUE (supplier_invoice_id, line_number),
    CONSTRAINT procurement_supplier_invoice_line_type_ck CHECK (line_type IN ('STOCK', 'PARTIAL_MATCH', 'NON_PO_RECEIPT', 'NON_STOCK')),
    CONSTRAINT procurement_supplier_invoice_line_amount_ck CHECK (
        (qty_invoiced IS NULL OR qty_invoiced >= 0) AND
        (unit_price IS NULL OR unit_price >= 0) AND
        tax_percent >= 0 AND tax_percent <= 100 AND
        tax_amount >= 0 AND line_total >= 0
    )
);

CREATE TABLE IF NOT EXISTS procurement.supplier_payment (
    id BIGSERIAL PRIMARY KEY,
    payment_number VARCHAR(100) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    payment_time TIMESTAMPTZ NOT NULL,
    transaction_ref VARCHAR(100),
    note TEXT,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_supplier_payment_method_ck CHECK (payment_method IN ('CASH', 'CARD', 'EWALLET', 'BANK_TRANSFER', 'CHEQUE', 'VOUCHER')),
    CONSTRAINT procurement_supplier_payment_amount_ck CHECK (amount > 0)
);

CREATE TABLE IF NOT EXISTS procurement.supplier_payment_allocation (
    supplier_payment_id BIGINT NOT NULL REFERENCES procurement.supplier_payment(id) ON DELETE CASCADE,
    supplier_invoice_id BIGINT NOT NULL REFERENCES procurement.supplier_invoice(id) ON DELETE CASCADE,
    allocated_amount NUMERIC(18, 2) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (supplier_payment_id, supplier_invoice_id),
    CONSTRAINT procurement_supplier_payment_allocation_ck CHECK (allocated_amount > 0)
);

CREATE TABLE IF NOT EXISTS procurement.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT procurement_outbox_status_ck CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_procurement_po_route ON procurement.purchase_order (region_id, outlet_id, order_date);
CREATE INDEX IF NOT EXISTS idx_procurement_po_status ON procurement.purchase_order (status);
CREATE INDEX IF NOT EXISTS idx_procurement_receipt_route ON procurement.goods_receipt (region_id, outlet_id, business_date);
CREATE INDEX IF NOT EXISTS idx_procurement_invoice_status ON procurement.supplier_invoice (status, invoice_date);
