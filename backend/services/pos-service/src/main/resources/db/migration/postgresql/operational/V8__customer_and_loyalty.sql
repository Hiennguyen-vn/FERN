-- ============================================================
-- V8: Customer & Loyalty Program
-- Adds customer master data and loyalty point tracking
-- to support F&B chain CRM and membership programs.
-- ============================================================

-- Customer master data
CREATE TABLE IF NOT EXISTS pos.customer (
    id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(150),
    dob DATE,
    gender VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    loyalty_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    loyalty_points BIGINT NOT NULL DEFAULT 0,
    total_spend NUMERIC(18, 2) NOT NULL DEFAULT 0,
    visit_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    note TEXT,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_customer_gender_ck CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT pos_customer_tier_ck CHECK (loyalty_tier IN ('STANDARD', 'SILVER', 'GOLD', 'PLATINUM', 'VIP')),
    CONSTRAINT pos_customer_status_ck CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT pos_customer_points_ck CHECK (loyalty_points >= 0),
    CONSTRAINT pos_customer_spend_ck CHECK (total_spend >= 0),
    CONSTRAINT pos_customer_visit_ck CHECK (visit_count >= 0)
);

-- Loyalty point transactions (earn / redeem / adjust / expire)
CREATE TABLE IF NOT EXISTS pos.loyalty_transaction (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES pos.customer(id),
    sale_order_id BIGINT REFERENCES pos.sale_order(id),
    outlet_id BIGINT,
    txn_type VARCHAR(20) NOT NULL,
    points INT NOT NULL,
    balance_after BIGINT NOT NULL,
    description VARCHAR(255),
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_loyalty_txn_type_ck CHECK (txn_type IN ('EARN', 'REDEEM', 'BONUS', 'EXPIRE', 'ADJUSTMENT')),
    CONSTRAINT pos_loyalty_balance_after_ck CHECK (balance_after >= 0)
);

-- Link customer to sale order (optional — not every order has a customer)
ALTER TABLE pos.sale_order
    ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES pos.customer(id);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_pos_customer_phone ON pos.customer (phone) WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pos_customer_email ON pos.customer (email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pos_customer_tier ON pos.customer (loyalty_tier, status);
CREATE INDEX IF NOT EXISTS idx_pos_customer_status ON pos.customer (status);
CREATE INDEX IF NOT EXISTS idx_pos_loyalty_txn_customer ON pos.loyalty_transaction (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pos_loyalty_txn_order ON pos.loyalty_transaction (sale_order_id) WHERE sale_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pos_sale_order_customer ON pos.sale_order (customer_id) WHERE customer_id IS NOT NULL;
