ALTER TABLE report.inventory_stock_snapshot
    ADD COLUMN IF NOT EXISTS inventory_value NUMERIC(18, 2) NOT NULL DEFAULT 0;

UPDATE report.inventory_stock_snapshot
SET inventory_value = ROUND(COALESCE(qty_on_hand, 0) * COALESCE(unit_cost, 0), 2)
WHERE COALESCE(inventory_value, 0) = 0;

CREATE TABLE IF NOT EXISTS report.payables_fact (
    fact_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    supplier_id BIGINT,
    supplier_invoice_id BIGINT,
    supplier_payment_id BIGINT,
    business_date DATE NOT NULL,
    currency_code VARCHAR,
    fact_type VARCHAR NOT NULL,
    amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2),
    matched_receipt_amount NUMERIC(18, 2),
    variance_amount NUMERIC(18, 2),
    payload JSONB,
    CONSTRAINT uk_payables_fact_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_payables_fact_invoice
    ON report.payables_fact (supplier_invoice_id, fact_type, business_date);

CREATE INDEX IF NOT EXISTS idx_payables_fact_supplier
    ON report.payables_fact (supplier_id, business_date);
