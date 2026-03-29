CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_outbox_payroll_run_event
    ON finance.outbox_event (aggregate_type, aggregate_id, event_type)
    WHERE aggregate_type = 'PAYROLL_RUN'
      AND event_type IN ('payroll.calculated', 'payroll.posted');
