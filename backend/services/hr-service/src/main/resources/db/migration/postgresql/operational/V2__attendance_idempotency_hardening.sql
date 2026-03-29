DELETE FROM hr.attendance_approval duplicate
USING hr.attendance_approval survivor
WHERE duplicate.shift_assignment_id = survivor.shift_assignment_id
  AND duplicate.id < survivor.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_attendance_approval_shift_assignment
    ON hr.attendance_approval (shift_assignment_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_outbox_attendance_approved
    ON hr.outbox_event (aggregate_type, aggregate_id, event_type)
    WHERE aggregate_type = 'ATTENDANCE_APPROVAL'
      AND event_type = 'attendance.approved';
