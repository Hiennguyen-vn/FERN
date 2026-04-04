-- BUG-012: Add unique index on source_order_id to enable fast lookup during
-- stale completion recovery and prevent duplicate reservations for the same order.
CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_reservation_source_order_id
    ON inventory.stock_reservation(source_order_id);
