ALTER TABLE pos.pos_session
    ADD COLUMN IF NOT EXISTS terminal_id VARCHAR(64);

DROP INDEX IF EXISTS pos.uq_pos_single_open_session_per_outlet;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pos_session_open_without_terminal
    ON pos.pos_session (outlet_id)
    WHERE status = 'OPEN' AND terminal_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pos_session_terminal_open
    ON pos.pos_session (outlet_id, terminal_id)
    WHERE status = 'OPEN' AND terminal_id IS NOT NULL;
