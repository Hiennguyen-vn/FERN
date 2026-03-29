CREATE SCHEMA IF NOT EXISTS config;

CREATE TABLE IF NOT EXISTS config.document_numbering_rule (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL UNIQUE,
    prefix VARCHAR(20),
    region_id BIGINT,
    outlet_id BIGINT,
    next_number BIGINT NOT NULL DEFAULT 1,
    reset_period VARCHAR(20) NOT NULL DEFAULT 'NEVER',
    format_pattern VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT config_document_numbering_rule_reset_ck CHECK (reset_period IN ('DAILY', 'MONTHLY', 'YEARLY', 'NEVER'))
);

CREATE TABLE IF NOT EXISTS config.system_policy (
    policy_key VARCHAR(100) PRIMARY KEY,
    policy_value JSONB NOT NULL,
    description TEXT,
    updated_by_user_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
