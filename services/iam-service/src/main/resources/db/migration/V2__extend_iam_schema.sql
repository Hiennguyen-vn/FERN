CREATE TABLE IF NOT EXISTS iam.user_permission_override (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES iam.user_account(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES iam.permission(id) ON DELETE CASCADE,
    override_mode VARCHAR(20) NOT NULL,
    reason TEXT,
    expires_at TIMESTAMPTZ,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT iam_user_permission_override_uk UNIQUE (user_id, permission_id),
    CONSTRAINT iam_user_permission_override_mode_ck CHECK (override_mode IN ('GRANT', 'DENY'))
);

CREATE TABLE IF NOT EXISTS iam.auth_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES iam.user_account(id) ON DELETE CASCADE,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    refresh_token_hash VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50),
    user_agent TEXT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT iam_auth_session_dates_ck CHECK (expires_at >= issued_at)
);

CREATE INDEX IF NOT EXISTS idx_iam_auth_session_user_id
    ON iam.auth_session (user_id, expires_at);
