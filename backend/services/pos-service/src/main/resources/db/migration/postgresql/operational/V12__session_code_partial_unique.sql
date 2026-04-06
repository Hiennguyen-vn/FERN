-- Replace the global UNIQUE constraint on session_code with a scoped partial index.
--
-- Rationale: the old constraint prevented reusing a session_code after a session
-- was closed (CLOSED / RECONCILED / CANCELLED).  For multi-outlet chains that use
-- predictable code conventions (e.g. OUTLET-001-YYYYMMDD), this caused spurious
-- conflicts when a terminal was reset or re-commissioned.
--
-- New invariant: a session_code must be unique *per outlet* only while the session
-- is OPEN.  Closed sessions are historical records and may share a code with future
-- sessions at the same outlet.

-- 1. Drop the old table-wide unique constraint.
ALTER TABLE pos.pos_session
    DROP CONSTRAINT IF EXISTS pos_session_session_code_key;

-- 2. Add a partial unique index: unique per outlet for OPEN sessions only.
CREATE UNIQUE INDEX uq_pos_session_outlet_code_open
    ON pos.pos_session (outlet_id, session_code)
    WHERE status = 'OPEN';

-- 3. Retain a non-unique index on session_code alone to keep lookup-by-code fast.
CREATE INDEX IF NOT EXISTS idx_pos_session_code
    ON pos.pos_session (session_code);
