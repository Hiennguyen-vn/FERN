ALTER TABLE finance.integration_event
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

UPDATE finance.integration_event
SET idempotency_key = NULLIF(payload ->> 'idempotencyKey', '')
WHERE idempotency_key IS NULL;

WITH ranked_duplicates AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY event_type, idempotency_key
               ORDER BY COALESCE(processed_at, received_at), id
           ) AS duplicate_rank
    FROM finance.integration_event
    WHERE idempotency_key IS NOT NULL
)
UPDATE finance.integration_event event
SET idempotency_key = NULL
FROM ranked_duplicates duplicates
WHERE event.id = duplicates.id
  AND duplicates.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_finance_integration_event_event_type_idempotency
    ON finance.integration_event (event_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
