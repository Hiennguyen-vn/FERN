-- L-01: Index for customer search using prefix matching.
-- Supports the PosCustomerService.searchCustomers() query that uses LOWER(field) LIKE 'query%'

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_lower_fullname
    ON pos.customer (LOWER(full_name) text_pattern_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_lower_phone
    ON pos.customer (LOWER(phone) text_pattern_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_lower_code
    ON pos.customer (LOWER(customer_code) text_pattern_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_lower_email
    ON pos.customer (LOWER(email) text_pattern_ops);

-- Index for loyalty_transaction lookups by customer
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loyalty_txn_customer
    ON pos.loyalty_transaction (customer_id, created_at DESC);
