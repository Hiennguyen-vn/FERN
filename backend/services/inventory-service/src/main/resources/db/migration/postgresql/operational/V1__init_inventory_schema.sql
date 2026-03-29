CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE IF NOT EXISTS inventory.inventory_transaction (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    qty_change NUMERIC(18, 4) NOT NULL,
    business_date DATE NOT NULL,
    txn_time TIMESTAMPTZ NOT NULL,
    txn_type VARCHAR(30) NOT NULL,
    unit_cost NUMERIC(18, 2),
    source_reference_type VARCHAR(50),
    source_reference_id VARCHAR(100),
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_transaction_type_ck CHECK (txn_type IN ('PURCHASE_IN', 'SALE_USAGE', 'WASTE_OUT', 'STOCK_ADJUSTMENT_IN', 'STOCK_ADJUSTMENT_OUT'))
);

CREATE TABLE IF NOT EXISTS inventory.stock_balance (
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    qty_on_hand NUMERIC(18, 4) NOT NULL DEFAULT 0,
    qty_reserved NUMERIC(18, 4) NOT NULL DEFAULT 0,
    qty_available NUMERIC(18, 4) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(18, 2),
    last_count_date DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (outlet_id, ingredient_id),
    CONSTRAINT inventory_stock_balance_qty_ck CHECK (
        qty_on_hand >= 0 AND qty_reserved >= 0 AND qty_available >= 0
    )
);

CREATE TABLE IF NOT EXISTS inventory.stock_count_session (
    id BIGSERIAL PRIMARY KEY,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    count_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    note TEXT,
    counted_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_stock_count_session_status_ck CHECK (status IN ('DRAFT', 'COUNTING', 'SUBMITTED', 'APPROVED', 'POSTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS inventory.stock_count_line (
    id BIGSERIAL PRIMARY KEY,
    stock_count_session_id BIGINT NOT NULL REFERENCES inventory.stock_count_session(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL,
    system_qty NUMERIC(18, 4) NOT NULL,
    actual_qty NUMERIC(18, 4) NOT NULL,
    variance_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_stock_count_line_uk UNIQUE (stock_count_session_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS inventory.waste_record (
    id BIGSERIAL PRIMARY KEY,
    inventory_transaction_id BIGINT NOT NULL UNIQUE REFERENCES inventory.inventory_transaction(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    reason VARCHAR(255) NOT NULL,
    submitted_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_waste_record_status_ck CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'POSTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS inventory.stock_adjustment (
    id BIGSERIAL PRIMARY KEY,
    inventory_transaction_id BIGINT NOT NULL UNIQUE REFERENCES inventory.inventory_transaction(id) ON DELETE CASCADE,
    adjustment_direction VARCHAR(10) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    approved_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_stock_adjustment_direction_ck CHECK (adjustment_direction IN ('IN', 'OUT'))
);

CREATE TABLE IF NOT EXISTS inventory.availability_projection (
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    ingredient_id BIGINT NOT NULL,
    qty_available NUMERIC(18, 4) NOT NULL DEFAULT 0,
    last_event_id VARCHAR(100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (outlet_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS inventory.inbox_event (
    id UUID PRIMARY KEY,
    source_event_id VARCHAR(100) NOT NULL,
    source_service VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    error_message TEXT,
    CONSTRAINT inventory_inbox_status_ck CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS inventory.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT inventory_outbox_status_ck CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_inventory_transaction_route ON inventory.inventory_transaction (region_id, outlet_id, business_date);
CREATE INDEX IF NOT EXISTS idx_inventory_balance_ingredient ON inventory.stock_balance (ingredient_id);
CREATE INDEX IF NOT EXISTS idx_inventory_inbox_source ON inventory.inbox_event (source_service, event_type, status);
