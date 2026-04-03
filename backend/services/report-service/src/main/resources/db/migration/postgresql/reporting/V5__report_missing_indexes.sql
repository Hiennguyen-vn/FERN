CREATE INDEX IF NOT EXISTS idx_report_outbox_aggregate
    ON report.outbox_event (aggregate_type, aggregate_id);

CREATE INDEX IF NOT EXISTS idx_report_outbox_published_at
    ON report.outbox_event (published_at);

DO $$
BEGIN
    IF to_regclass('report.region_daily_summary') IS NOT NULL THEN
        EXECUTE '
            CREATE UNIQUE INDEX IF NOT EXISTS uk_report_region_daily_summary_region_business_date
                ON report.region_daily_summary (region_id, business_date)
        ';
    END IF;
    IF to_regclass('report.region_daily_summary') IS NOT NULL
            AND to_regclass('report.region_daily_event') IS NOT NULL
            AND NOT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conrelid = 'report.region_daily_event'::regclass
                  AND conname = 'fk_region_daily_event_summary'
            ) THEN
        EXECUTE '
            ALTER TABLE report.region_daily_event
                ADD CONSTRAINT fk_region_daily_event_summary
                    FOREIGN KEY (region_id, business_date)
                    REFERENCES report.region_daily_summary (region_id, business_date)
                    DEFERRABLE INITIALLY DEFERRED
        ';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('report.company_daily_summary') IS NOT NULL THEN
        EXECUTE '
            CREATE UNIQUE INDEX IF NOT EXISTS uk_report_company_daily_summary_business_date
                ON report.company_daily_summary (business_date)
        ';
    END IF;
    IF to_regclass('report.company_daily_summary') IS NOT NULL
            AND to_regclass('report.company_daily_outlet') IS NOT NULL
            AND NOT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conrelid = 'report.company_daily_outlet'::regclass
                  AND conname = 'fk_company_daily_outlet_summary'
            ) THEN
        EXECUTE '
            ALTER TABLE report.company_daily_outlet
                ADD CONSTRAINT fk_company_daily_outlet_summary
                    FOREIGN KEY (business_date)
                    REFERENCES report.company_daily_summary (business_date)
                    DEFERRABLE INITIALLY DEFERRED
        ';
    END IF;
END $$;
