CREATE SCHEMA IF NOT EXISTS finance_projection;

CREATE TABLE IF NOT EXISTS finance_projection.accounting_posting_projection (
    posting_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    account_code VARCHAR NOT NULL,
    debit_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    credit_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    currency_code VARCHAR,
    reference_type VARCHAR,
    reference_id VARCHAR,
    payload JSONB,
    CONSTRAINT uk_accounting_posting_projection_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_accounting_posting_projection_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS finance_projection.reconciliation_snapshot (
    snapshot_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    region_id BIGINT,
    outlet_id BIGINT,
    business_date DATE NOT NULL,
    snapshot_type VARCHAR NOT NULL,
    snapshot_value NUMERIC(18, 2) NOT NULL DEFAULT 0,
    snapshot_payload JSONB,
    CONSTRAINT uk_reconciliation_snapshot_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_reconciliation_snapshot_idempotency UNIQUE (idempotency_key)
);
