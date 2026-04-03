-- M5: Human-readable PO/GR reference numbers using a shared sequence.
-- Format: PO-YYYYMM-000001, GR-YYYYMM-000002
CREATE SEQUENCE IF NOT EXISTS procurement.reference_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 10;
