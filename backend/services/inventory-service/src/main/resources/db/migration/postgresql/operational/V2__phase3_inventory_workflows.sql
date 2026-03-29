ALTER TABLE inventory.stock_count_line
    ALTER COLUMN actual_qty DROP NOT NULL;

ALTER TABLE inventory.waste_record
    ALTER COLUMN inventory_transaction_id DROP NOT NULL;

ALTER TABLE inventory.stock_adjustment
    ALTER COLUMN inventory_transaction_id DROP NOT NULL;

ALTER TABLE inventory.stock_adjustment
    ADD COLUMN IF NOT EXISTS region_id BIGINT,
    ADD COLUMN IF NOT EXISTS outlet_id BIGINT,
    ADD COLUMN IF NOT EXISTS ingredient_id BIGINT,
    ADD COLUMN IF NOT EXISTS qty NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS business_date DATE,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS note TEXT,
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS posted_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS posted_at TIMESTAMPTZ;

ALTER TABLE inventory.waste_record
    ADD COLUMN IF NOT EXISTS region_id BIGINT,
    ADD COLUMN IF NOT EXISTS outlet_id BIGINT,
    ADD COLUMN IF NOT EXISTS ingredient_id BIGINT,
    ADD COLUMN IF NOT EXISTS qty NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS business_date DATE,
    ADD COLUMN IF NOT EXISTS note TEXT,
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS posted_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS posted_at TIMESTAMPTZ;

ALTER TABLE inventory.stock_count_session
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS posted_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS posted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS inventory.stock_reservation (
    id BIGSERIAL PRIMARY KEY,
    outlet_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    source_order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    committed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_stock_reservation_status_ck CHECK (status IN ('RESERVED', 'COMMITTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS inventory.stock_reservation_line (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES inventory.stock_reservation(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL,
    ingredient_code VARCHAR(50),
    ingredient_name VARCHAR(150),
    uom_code VARCHAR(30),
    qty NUMERIC(18, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inventory.idempotency_request (
    id BIGSERIAL PRIMARY KEY,
    operation VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    resource_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_idempotency_request_uk UNIQUE (operation, idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_inbox_source_event
    ON inventory.inbox_event (source_event_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_stock_reservation_source_order
    ON inventory.stock_reservation (source_order_id);
