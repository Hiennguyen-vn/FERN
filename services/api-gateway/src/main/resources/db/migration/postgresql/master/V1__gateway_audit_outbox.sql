CREATE SCHEMA IF NOT EXISTS gateway;

CREATE TABLE IF NOT EXISTS gateway.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT gateway_outbox_status_ck CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_gateway_outbox_pending
    ON gateway.outbox_event (status, created_at);

CREATE INDEX IF NOT EXISTS idx_gateway_outbox_aggregate
    ON gateway.outbox_event (aggregate_type, aggregate_id);
