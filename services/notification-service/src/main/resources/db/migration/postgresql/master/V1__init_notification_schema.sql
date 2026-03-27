CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE IF NOT EXISTS notification.alert_rule (
    alert_rule_id BIGINT PRIMARY KEY,
    rule_name VARCHAR NOT NULL,
    event_type VARCHAR NOT NULL,
    condition_expression VARCHAR,
    notification_channel VARCHAR NOT NULL,
    recipients JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification.webhook_endpoint (
    webhook_endpoint_id BIGINT PRIMARY KEY,
    endpoint_url VARCHAR NOT NULL,
    event_types JSONB NOT NULL,
    secret_key_ref VARCHAR,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification.notification_job (
    notification_job_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR,
    source_service VARCHAR,
    event_type VARCHAR,
    occurred_at TIMESTAMPTZ,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR,
    notification_type VARCHAR NOT NULL,
    channel VARCHAR NOT NULL,
    recipient VARCHAR,
    subject VARCHAR,
    body VARCHAR,
    status VARCHAR NOT NULL,
    scheduled_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    payload JSONB,
    CONSTRAINT uk_notification_job_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_notification_job_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE IF NOT EXISTS notification.delivery_attempt (
    delivery_attempt_id BIGINT PRIMARY KEY,
    notification_job_id BIGINT NOT NULL REFERENCES notification.notification_job(notification_job_id),
    attempt_number INTEGER NOT NULL,
    attempt_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR NOT NULL,
    error_message VARCHAR,
    response_code VARCHAR
);

CREATE TABLE IF NOT EXISTS notification.webhook_delivery_log (
    webhook_delivery_log_id BIGINT PRIMARY KEY,
    source_event_id VARCHAR,
    source_service VARCHAR,
    event_type VARCHAR,
    occurred_at TIMESTAMPTZ,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR,
    webhook_endpoint_id BIGINT NOT NULL REFERENCES notification.webhook_endpoint(webhook_endpoint_id),
    delivery_status VARCHAR NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    payload JSONB,
    CONSTRAINT uk_webhook_delivery_log_source_event UNIQUE (source_event_id),
    CONSTRAINT uk_webhook_delivery_log_idempotency UNIQUE (idempotency_key)
);
