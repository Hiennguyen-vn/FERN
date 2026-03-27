CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE IF NOT EXISTS iam.permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS iam.role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS iam.role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES iam.role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES iam.permission(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT iam_role_permission_uk UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS iam.user_account (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    password_changed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS iam.user_role_assignment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES iam.user_account(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES iam.role(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT iam_user_role_assignment_uk UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS iam.user_scope_assignment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES iam.user_account(id) ON DELETE CASCADE,
    scope_type VARCHAR(20) NOT NULL,
    scope_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT iam_user_scope_assignment_uk UNIQUE (user_id, scope_type, scope_id)
);

CREATE TABLE IF NOT EXISTS iam.policy_version_state (
    id INT PRIMARY KEY,
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT iam_policy_version_singleton_ck CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS iam.scope_version_state (
    id INT PRIMARY KEY,
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT iam_scope_version_singleton_ck CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS iam.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

INSERT INTO iam.permission (code, name, description, created_at, updated_at)
VALUES
    ('iam.user.read', 'Read users', 'Read IAM user records', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.user.write', 'Write users', 'Create and update IAM users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.role.read', 'Read roles', 'Read IAM roles', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.role.write', 'Write roles', 'Create IAM roles', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.role.assign', 'Assign roles', 'Assign roles to users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.scope.assign', 'Assign scopes', 'Assign scope roots to users', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('iam.permission.read', 'Read permissions', 'Read permission catalog', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('org.region.read', 'Read regions', 'Read organization regions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('org.region.write', 'Write regions', 'Create and update regions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('org.outlet.read', 'Read outlets', 'Read organization outlets', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('org.outlet.write', 'Write outlets', 'Create and update outlets', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('org.scope.resolve', 'Resolve scopes', 'Expand scope roots for internal org resolution', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role (id, code, name, description, status, created_at, updated_at)
VALUES (1, 'bootstrap_admin', 'Bootstrap Admin', 'Local bootstrap administrator for development', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO iam.user_account (id, username, password_hash, full_name, email, phone, status, password_changed_at, created_at, updated_at)
VALUES (
    1,
    'bootstrap-admin',
    '$argon2id$v=19$m=65536,t=3,p=4$mkoVnK7P1XT3SJVY12kB6Q$Zlf+9gA1+gyBC2YQJoQyDr2UvlP291f+9128opEMFFs',
    'Bootstrap Admin',
    'bootstrap-admin@fern.local',
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO iam.role_permission (role_id, permission_id, created_at)
SELECT 1, permission.id, CURRENT_TIMESTAMP
FROM iam.permission permission
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO iam.user_role_assignment (user_id, role_id, created_at)
VALUES (1, 1, CURRENT_TIMESTAMP)
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO iam.user_scope_assignment (user_id, scope_type, scope_id, created_at)
VALUES (1, 'REGION', 1, CURRENT_TIMESTAMP)
ON CONFLICT (user_id, scope_type, scope_id) DO NOTHING;

INSERT INTO iam.policy_version_state (id, version, updated_at)
VALUES (1, 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO iam.scope_version_state (id, version, updated_at)
VALUES (1, 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('iam.role_id_seq', GREATEST((SELECT MAX(id) FROM iam.role), 1), true);
SELECT setval('iam.user_account_id_seq', GREATEST((SELECT MAX(id) FROM iam.user_account), 1), true);
