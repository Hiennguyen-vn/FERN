-- Dine-in table management and promotion support

-- Add promotion_code and table_id columns to sale_order
ALTER TABLE pos.sale_order ADD COLUMN IF NOT EXISTS promotion_code VARCHAR(100);
ALTER TABLE pos.sale_order ADD COLUMN IF NOT EXISTS table_id BIGINT;

-- Create dining_table table
CREATE TABLE IF NOT EXISTS pos.dining_table (
    id               BIGSERIAL   PRIMARY KEY,
    outlet_id        BIGINT      NOT NULL,
    table_name       VARCHAR(50) NOT NULL,
    table_code       VARCHAR(30) NOT NULL,
    capacity         INT,
    zone             VARCHAR(50),
    status           VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    current_order_id BIGINT,
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Each outlet has unique table codes
    CONSTRAINT uq_dining_table_outlet_code UNIQUE (outlet_id, table_code)
);

-- Index for fast lookup by outlet and status
CREATE INDEX IF NOT EXISTS idx_dining_table_outlet_status
    ON pos.dining_table(outlet_id, status);

-- Foreign key from sale_order to dining_table
ALTER TABLE pos.sale_order
    ADD CONSTRAINT fk_sale_order_table_id
    FOREIGN KEY (table_id) REFERENCES pos.dining_table(id);
