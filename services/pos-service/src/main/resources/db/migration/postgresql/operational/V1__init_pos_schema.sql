CREATE SCHEMA IF NOT EXISTS pos;

CREATE TABLE IF NOT EXISTS pos.pos_session (
    id BIGSERIAL PRIMARY KEY,
    session_code VARCHAR(100) NOT NULL UNIQUE,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    cashier_user_id BIGINT,
    manager_user_id BIGINT,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    business_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_pos_session_status_ck CHECK (status IN ('OPEN', 'CLOSED', 'RECONCILED', 'CANCELLED')),
    CONSTRAINT pos_pos_session_dates_ck CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE TABLE IF NOT EXISTS pos.sale_order (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    region_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    pos_session_id BIGINT REFERENCES pos.pos_session(id),
    currency_code VARCHAR(10) NOT NULL,
    order_type VARCHAR(20) NOT NULL DEFAULT 'DINE_IN',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    subtotal NUMERIC(18, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_sale_order_type_ck CHECK (order_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY', 'ONLINE')),
    CONSTRAINT pos_sale_order_status_ck CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'VOIDED')),
    CONSTRAINT pos_sale_order_payment_status_ck CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'REFUNDED')),
    CONSTRAINT pos_sale_order_amount_ck CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0 AND discount_amount <= subtotal
    )
);

CREATE TABLE IF NOT EXISTS pos.sale_order_line (
    id BIGSERIAL PRIMARY KEY,
    sale_order_id BIGINT NOT NULL REFERENCES pos.sale_order(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    product_id BIGINT NOT NULL,
    product_code VARCHAR(50),
    product_name_snapshot VARCHAR(150) NOT NULL,
    unit_price NUMERIC(18, 2) NOT NULL,
    qty NUMERIC(18, 4) NOT NULL,
    discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    line_total NUMERIC(18, 2) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_sale_order_line_uk UNIQUE (sale_order_id, line_number),
    CONSTRAINT pos_sale_order_line_amount_ck CHECK (
        unit_price >= 0 AND qty > 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0
    )
);

CREATE TABLE IF NOT EXISTS pos.sale_payment (
    id BIGSERIAL PRIMARY KEY,
    sale_order_id BIGINT NOT NULL REFERENCES pos.sale_order(id) ON DELETE CASCADE,
    pos_session_id BIGINT REFERENCES pos.pos_session(id),
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_time TIMESTAMPTZ NOT NULL,
    transaction_ref VARCHAR(100),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_sale_payment_method_ck CHECK (payment_method IN ('CASH', 'CARD', 'EWALLET', 'BANK_TRANSFER', 'CHEQUE', 'VOUCHER')),
    CONSTRAINT pos_sale_payment_status_ck CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT pos_sale_payment_amount_ck CHECK (amount >= 0)
);

CREATE TABLE IF NOT EXISTS pos.sale_snapshot (
    sale_order_id BIGINT PRIMARY KEY REFERENCES pos.sale_order(id) ON DELETE CASCADE,
    order_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pos.promotion (
    id BIGSERIAL PRIMARY KEY,
    promotion_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    promo_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    value_amount NUMERIC(18, 2),
    value_percent NUMERIC(8, 4),
    min_order_amount NUMERIC(18, 2),
    max_discount_amount NUMERIC(18, 2),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_promotion_type_ck CHECK (promo_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'BUY_X_GET_Y', 'COMBO_PRICE', 'SUBSIDY')),
    CONSTRAINT pos_promotion_status_ck CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT pos_promotion_dates_ck CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE IF NOT EXISTS pos.promotion_scope (
    promotion_id BIGINT NOT NULL REFERENCES pos.promotion(id) ON DELETE CASCADE,
    outlet_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (promotion_id, outlet_id)
);

CREATE TABLE IF NOT EXISTS pos.sale_order_line_promotion (
    sale_order_line_id BIGINT NOT NULL REFERENCES pos.sale_order_line(id) ON DELETE CASCADE,
    promotion_id BIGINT NOT NULL REFERENCES pos.promotion(id) ON DELETE CASCADE,
    discount_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sale_order_line_id, promotion_id)
);

CREATE TABLE IF NOT EXISTS pos.refund_request (
    id BIGSERIAL PRIMARY KEY,
    sale_order_id BIGINT NOT NULL REFERENCES pos.sale_order(id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    reason TEXT,
    approved_by_user_id BIGINT,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_refund_request_status_ck CHECK (status IN ('DRAFT', 'APPROVED', 'REFUNDED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS pos.void_request (
    id BIGSERIAL PRIMARY KEY,
    sale_order_id BIGINT NOT NULL REFERENCES pos.sale_order(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    approved_by_user_id BIGINT,
    voided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pos_void_request_status_ck CHECK (status IN ('PENDING', 'APPROVED', 'VOIDED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS pos.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT pos_outbox_status_ck CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_pos_session_route ON pos.pos_session (region_id, outlet_id, business_date);
CREATE INDEX IF NOT EXISTS idx_pos_sale_order_route ON pos.sale_order (region_id, outlet_id, created_at);
CREATE INDEX IF NOT EXISTS idx_pos_sale_order_status ON pos.sale_order (status, payment_status);
CREATE INDEX IF NOT EXISTS idx_pos_sale_payment_time ON pos.sale_payment (payment_time, status);
