ALTER TABLE notification.notification_job
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_delivery_attempt_job_attempt
    ON notification.delivery_attempt (notification_job_id, attempt_number);

CREATE INDEX IF NOT EXISTS idx_notification_job_pending_schedule
    ON notification.notification_job (status, scheduled_at, notification_job_id);
