-- L-01: Composite indices for frequently queried paths identified during architectural audit.

-- Inventory: transactions are commonly queried by (outlet_id, business_date) with optional txn_type filter.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory_txn_outlet_bdate
    ON inventory.inventory_transaction (outlet_id, business_date, txn_type);

-- Inventory: transactions queried by (outlet_id, ingredient_id) for ingredient-level ledger views.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory_txn_outlet_ingredient
    ON inventory.inventory_transaction (outlet_id, ingredient_id);

-- Inventory: stock_balance queried with last_count_date (for overdue count detection).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stock_balance_last_count
    ON inventory.stock_balance (outlet_id, last_count_date);

-- Inventory: expired reservations cleanup (scheduled job).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reservation_status_expires
    ON inventory.stock_reservation (status, expires_at)
    WHERE status = 'RESERVED';
