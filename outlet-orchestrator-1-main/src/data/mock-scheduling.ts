import type { Shift, SwapRequest, TimeOffRequest, OvertimeSummary } from '@/types/scheduling';

const today = '2026-04-05';

export const mockEmployeePool = [
  { id: 'emp-001', name: 'Aisha Patel', role: 'Barista' },
  { id: 'emp-002', name: 'David Nguyen', role: 'Cashier' },
  { id: 'emp-003', name: 'Maria Santos', role: 'Kitchen Staff' },
  { id: 'emp-004', name: 'James Lee', role: 'Barista' },
  { id: 'emp-005', name: 'Linh Tran', role: 'Supervisor' },
  { id: 'emp-006', name: 'Ahmad Hassan', role: 'Kitchen Staff' },
  { id: 'emp-007', name: 'Sophie Chen', role: 'Barista' },
  { id: 'emp-008', name: 'Marcus Rivera', role: 'Manager' },
];

export const mockShifts: Shift[] = [
  {
    id: 'shift-001', outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: today, shiftType: 'morning', startTime: '06:00', endTime: '14:00', breakMinutes: 60,
    status: 'in_progress',
    assignments: [
      { id: 'asg-001', employeeId: 'emp-001', employeeName: 'Aisha Patel', employeeRole: 'Barista', status: 'checked_in', overtimeMinutes: 0 },
      { id: 'asg-002', employeeId: 'emp-002', employeeName: 'David Nguyen', employeeRole: 'Cashier', status: 'checked_in', overtimeMinutes: 0 },
    ],
  },
  {
    id: 'shift-002', outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: today, shiftType: 'afternoon', startTime: '14:00', endTime: '22:00', breakMinutes: 60,
    status: 'scheduled',
    assignments: [
      { id: 'asg-003', employeeId: 'emp-003', employeeName: 'Maria Santos', employeeRole: 'Kitchen Staff', status: 'assigned', overtimeMinutes: 0 },
    ],
  },
  {
    id: 'shift-003', outletId: 'outlet-002', outletName: 'Riverside Branch',
    date: today, shiftType: 'morning', startTime: '06:30', endTime: '14:30', breakMinutes: 60,
    status: 'completed',
    assignments: [
      { id: 'asg-004', employeeId: 'emp-004', employeeName: 'James Lee', employeeRole: 'Barista', status: 'checked_out', overtimeMinutes: 30 },
      { id: 'asg-005', employeeId: 'emp-005', employeeName: 'Linh Tran', employeeRole: 'Supervisor', status: 'checked_out', overtimeMinutes: 0 },
    ],
  },
  {
    id: 'shift-004', outletId: 'outlet-003', outletName: 'Mall Kiosk A',
    date: today, shiftType: 'split', startTime: '10:00', endTime: '21:00', breakMinutes: 120,
    status: 'in_progress',
    assignments: [
      { id: 'asg-006', employeeId: 'emp-006', employeeName: 'Ahmad Hassan', employeeRole: 'Kitchen Staff', status: 'checked_in', overtimeMinutes: 0 },
    ],
  },
  {
    id: 'shift-005', outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-06', shiftType: 'morning', startTime: '06:00', endTime: '14:00', breakMinutes: 60,
    status: 'scheduled',
    assignments: [
      { id: 'asg-007', employeeId: 'emp-007', employeeName: 'Sophie Chen', employeeRole: 'Barista', status: 'assigned', overtimeMinutes: 0 },
      { id: 'asg-008', employeeId: 'emp-001', employeeName: 'Aisha Patel', employeeRole: 'Barista', status: 'assigned', overtimeMinutes: 0 },
    ],
  },
];

export const mockSwapRequests: SwapRequest[] = [
  { id: 'swap-001', requesterId: 'emp-001', requesterName: 'Aisha Patel', targetId: 'emp-007', targetName: 'Sophie Chen', originalShiftId: 'shift-005', originalDate: '2026-04-06', originalShift: 'Morning (06:00-14:00)', targetShiftId: 'shift-005', targetDate: '2026-04-06', targetShift: 'Morning (06:00-14:00)', reason: 'Doctor appointment in the evening', status: 'pending', createdAt: '2026-04-04T10:30:00Z' },
  { id: 'swap-002', requesterId: 'emp-003', requesterName: 'Maria Santos', targetId: 'emp-006', targetName: 'Ahmad Hassan', originalShiftId: 'shift-002', originalDate: today, originalShift: 'Afternoon (14:00-22:00)', targetShiftId: 'shift-004', targetDate: today, targetShift: 'Split (10:00-21:00)', reason: 'Personal commitment', status: 'rejected', reviewedBy: 'Marcus Rivera', reviewedAt: '2026-04-04T14:00:00Z', createdAt: '2026-04-03T16:00:00Z' },
];

export const mockTimeOffRequests: TimeOffRequest[] = [
  { id: 'to-001', employeeId: 'emp-002', employeeName: 'David Nguyen', employeeRole: 'Cashier', outletName: 'Downtown Flagship', type: 'annual', startDate: '2026-04-10', endDate: '2026-04-12', days: 3, reason: 'Family trip', status: 'approved', reviewedBy: 'Marcus Rivera', reviewedAt: '2026-04-02T09:00:00Z', createdAt: '2026-03-28T08:00:00Z' },
  { id: 'to-002', employeeId: 'emp-005', employeeName: 'Linh Tran', employeeRole: 'Supervisor', outletName: 'Riverside Branch', type: 'sick', startDate: '2026-04-07', endDate: '2026-04-07', days: 1, reason: 'Not feeling well', status: 'pending', createdAt: '2026-04-05T07:00:00Z' },
  { id: 'to-003', employeeId: 'emp-004', employeeName: 'James Lee', employeeRole: 'Barista', outletName: 'Riverside Branch', type: 'personal', startDate: '2026-04-15', endDate: '2026-04-16', days: 2, reason: 'Moving house', status: 'pending', createdAt: '2026-04-04T11:00:00Z' },
];

export const mockOvertimeSummary: OvertimeSummary[] = [
  { employeeId: 'emp-004', employeeName: 'James Lee', outletName: 'Riverside Branch', periodStart: '2026-04-01', periodEnd: '2026-04-05', scheduledHours: 40, actualHours: 43.5, overtimeHours: 3.5, overtimeRate: 1.5, overtimePay: 262500 },
  { employeeId: 'emp-006', employeeName: 'Ahmad Hassan', outletName: 'Mall Kiosk A', periodStart: '2026-04-01', periodEnd: '2026-04-05', scheduledHours: 40, actualHours: 45, overtimeHours: 5, overtimeRate: 1.5, overtimePay: 375000 },
  { employeeId: 'emp-001', employeeName: 'Aisha Patel', outletName: 'Downtown Flagship', periodStart: '2026-04-01', periodEnd: '2026-04-05', scheduledHours: 40, actualHours: 41, overtimeHours: 1, overtimeRate: 1.5, overtimePay: 75000 },
];
