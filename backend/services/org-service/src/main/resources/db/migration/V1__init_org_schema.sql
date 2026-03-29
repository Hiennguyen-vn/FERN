CREATE SCHEMA IF NOT EXISTS org;

CREATE TABLE IF NOT EXISTS org.currency (
    code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(10),
    decimal_places INT NOT NULL DEFAULT 2,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS org.region (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    parent_region_id BIGINT REFERENCES org.region(id),
    currency_code VARCHAR(10) NOT NULL REFERENCES org.currency(code),
    name VARCHAR(150) NOT NULL,
    tax_code VARCHAR(50),
    timezone_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS org.outlet (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT NOT NULL REFERENCES org.region(id),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    address TEXT,
    phone VARCHAR(30),
    email VARCHAR(150),
    opened_at DATE,
    closed_at DATE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outlet_dates_ck CHECK (closed_at IS NULL OR opened_at IS NULL OR closed_at >= opened_at)
);

CREATE TABLE IF NOT EXISTS org.region_closure (
    ancestor_region_id BIGINT NOT NULL REFERENCES org.region(id) ON DELETE CASCADE,
    descendant_region_id BIGINT NOT NULL REFERENCES org.region(id) ON DELETE CASCADE,
    depth INT NOT NULL,
    PRIMARY KEY (ancestor_region_id, descendant_region_id)
);

CREATE TABLE IF NOT EXISTS org.scope_version_state (
    id INT PRIMARY KEY,
    version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT org_scope_version_state_singleton_ck CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS org.outbox_event (
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

INSERT INTO org.currency (code, name, symbol, decimal_places, created_at, updated_at)
VALUES ('VND', 'Vietnamese Dong', '₫', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO org.region (id, code, parent_region_id, currency_code, name, tax_code, timezone_name, created_at, updated_at)
VALUES (1, 'ROOT', NULL, 'VND', 'Company Root', NULL, 'Asia/Ho_Chi_Minh', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO org.region_closure (ancestor_region_id, descendant_region_id, depth)
VALUES (1, 1, 0)
ON CONFLICT (ancestor_region_id, descendant_region_id) DO NOTHING;

INSERT INTO org.scope_version_state (id, version, updated_at)
VALUES (1, 1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

SELECT setval('org.region_id_seq', GREATEST((SELECT MAX(id) FROM org.region), 1), true);
