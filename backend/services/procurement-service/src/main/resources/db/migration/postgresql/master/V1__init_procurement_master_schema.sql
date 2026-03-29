CREATE SCHEMA IF NOT EXISTS procurement_master;

CREATE TABLE IF NOT EXISTS procurement_master.supplier (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    tax_code VARCHAR(50),
    email VARCHAR(150),
    phone VARCHAR(30),
    address TEXT,
    default_region_id BIGINT,
    status VARCHAR(20) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT procurement_master_supplier_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE TABLE IF NOT EXISTS procurement_master.supplier_contact (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES procurement_master.supplier(id) ON DELETE CASCADE,
    contact_name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(30),
    role_title VARCHAR(100),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS procurement_master.supplier_region_coverage (
    supplier_id BIGINT NOT NULL REFERENCES procurement_master.supplier(id) ON DELETE CASCADE,
    region_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (supplier_id, region_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_procurement_master_supplier_code_active
    ON procurement_master.supplier (supplier_code) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_procurement_master_supplier_tax_code_active
    ON procurement_master.supplier (tax_code) WHERE deleted_at IS NULL AND tax_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_procurement_master_supplier_status
    ON procurement_master.supplier (status);
