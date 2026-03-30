CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE hr.shift_assignment
    ADD COLUMN IF NOT EXISTS shift_start_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS shift_end_at TIMESTAMP;

UPDATE hr.shift_assignment sa
SET shift_start_at = (s.shift_date::timestamp + s.start_time),
    shift_end_at = CASE
        WHEN s.end_time > s.start_time THEN (s.shift_date::timestamp + s.end_time)
        ELSE ((s.shift_date + 1)::timestamp + s.end_time)
    END
FROM hr.shift_schedule s
WHERE s.id = sa.shift_schedule_id
  AND (sa.shift_start_at IS NULL OR sa.shift_end_at IS NULL);

ALTER TABLE hr.shift_assignment
    ALTER COLUMN shift_start_at SET NOT NULL,
    ALTER COLUMN shift_end_at SET NOT NULL;

ALTER TABLE hr.shift_assignment
    DROP CONSTRAINT IF EXISTS hr_shift_assignment_no_overlap;

ALTER TABLE hr.shift_assignment
    ADD CONSTRAINT hr_shift_assignment_no_overlap
    EXCLUDE USING gist (
        employee_id WITH =,
        tsrange(shift_start_at, shift_end_at, '[)') WITH &&
    );
