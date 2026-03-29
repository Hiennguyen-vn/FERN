CREATE TABLE IF NOT EXISTS org.exchange_rate (
    from_currency_code VARCHAR(10) NOT NULL REFERENCES org.currency(code),
    to_currency_code VARCHAR(10) NOT NULL REFERENCES org.currency(code),
    rate NUMERIC(20, 8) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_currency_code, to_currency_code, effective_from),
    CONSTRAINT org_exchange_rate_dates_ck CHECK (
        from_currency_code <> to_currency_code AND
        rate > 0 AND
        (effective_to IS NULL OR effective_to >= effective_from)
    )
);

CREATE TABLE IF NOT EXISTS org.legal_entity (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT REFERENCES org.region(id),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    tax_code VARCHAR(50),
    registration_number VARCHAR(100),
    address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS org.outlet_config (
    outlet_id BIGINT PRIMARY KEY REFERENCES org.outlet(id) ON DELETE CASCADE,
    timezone_name VARCHAR(100),
    currency_code VARCHAR(10) REFERENCES org.currency(code),
    is_pos_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_inventory_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_procurement_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_org_legal_entity_region_id
    ON org.legal_entity (region_id);
