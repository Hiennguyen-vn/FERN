CREATE TABLE IF NOT EXISTS report.outbox_event (
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
    CONSTRAINT report_outbox_status_ck CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS report.region_daily_event (
    region_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    source_event_id VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (region_id, business_date, source_event_id)
);

CREATE TABLE IF NOT EXISTS report.company_daily_outlet (
    business_date DATE NOT NULL,
    outlet_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (business_date, outlet_id)
);

ALTER TABLE report.export_job
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR;

CREATE INDEX IF NOT EXISTS idx_report_outbox_pending
    ON report.outbox_event (status, created_at);
