ALTER TABLE finance.expense_record
    ADD COLUMN IF NOT EXISTS source_event_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_reference_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_reference_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS posted_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_expense_record_source_event
    ON finance.expense_record (source_event_id)
    WHERE source_event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_expense_inventory_purchase_goods_receipt
    ON finance.expense_inventory_purchase (goods_receipt_id);

CREATE TABLE IF NOT EXISTS finance.integration_event (
    id BIGSERIAL PRIMARY KEY,
    source_event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    error_message TEXT,
    CONSTRAINT finance_integration_event_status_ck CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
);
