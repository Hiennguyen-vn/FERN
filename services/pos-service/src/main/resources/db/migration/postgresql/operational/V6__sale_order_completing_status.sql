ALTER TABLE pos.sale_order
    DROP CONSTRAINT IF EXISTS pos_sale_order_status_ck;

ALTER TABLE pos.sale_order
    ADD CONSTRAINT pos_sale_order_status_ck
        CHECK (status IN ('OPEN', 'COMPLETING', 'COMPLETED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'VOIDED'));
