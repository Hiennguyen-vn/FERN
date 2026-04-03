CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS audit.audit_event (
    audit_event_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    module VARCHAR,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    correlation_id VARCHAR,
    region_id BIGINT,
    outlet_id BIGINT,
    user_id BIGINT,
    action VARCHAR,
    resource_type VARCHAR,
    resource_id VARCHAR,
    outcome VARCHAR,
    old_value JSONB,
    new_value JSONB,
    payload JSONB,
    CONSTRAINT uk_audit_event_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_audit_event_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_audit_event_occurred_at ON audit.audit_event (occurred_at DESC, audit_event_id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_correlation_id ON audit.audit_event (correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_scope ON audit.audit_event (region_id, outlet_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor ON audit.audit_event (user_id);

CREATE TABLE IF NOT EXISTS audit.security_event (
    security_event_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    module VARCHAR,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    correlation_id VARCHAR,
    user_id BIGINT,
    outcome VARCHAR,
    failure_reason VARCHAR,
    ip_address VARCHAR,
    user_agent VARCHAR,
    payload JSONB,
    CONSTRAINT uk_security_event_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_security_event_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_security_event_occurred_at ON audit.security_event (occurred_at DESC, security_event_id DESC);
CREATE INDEX IF NOT EXISTS idx_security_event_correlation_id ON audit.security_event (correlation_id);
CREATE INDEX IF NOT EXISTS idx_security_event_actor ON audit.security_event (user_id);

CREATE TABLE IF NOT EXISTS audit.request_trace (
    request_trace_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR NOT NULL,
    source_service VARCHAR NOT NULL,
    module VARCHAR,
    event_type VARCHAR NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR NOT NULL,
    correlation_id VARCHAR NOT NULL,
    request_id VARCHAR,
    endpoint VARCHAR NOT NULL,
    method VARCHAR NOT NULL,
    status_code INTEGER,
    duration_ms BIGINT,
    region_id BIGINT,
    outlet_id BIGINT,
    user_id BIGINT,
    payload JSONB,
    CONSTRAINT uk_request_trace_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_request_trace_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_request_trace_occurred_at ON audit.request_trace (occurred_at DESC, request_trace_id DESC);
CREATE INDEX IF NOT EXISTS idx_request_trace_correlation_id ON audit.request_trace (correlation_id);
CREATE INDEX IF NOT EXISTS idx_request_trace_scope ON audit.request_trace (region_id, outlet_id);
CREATE INDEX IF NOT EXISTS idx_request_trace_actor ON audit.request_trace (user_id);
