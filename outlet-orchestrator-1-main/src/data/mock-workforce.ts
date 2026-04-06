import type { AttendanceRecord, OvertimeRecord, LeaveRequest, LeaveQuota } from '@/types/workforce';

export const mockAttendance: AttendanceRecord[] = [
  {
    id: 'att-001', employeeId: 'emp-001', employeeName: 'Nguyễn Văn An',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-05', checkIn: '07:58', checkOut: null,
    scheduledIn: '08:00', scheduledOut: '16:00',
    status: 'checked_in', lateMinutes: 0, overtimeMinutes: 0,
  },
  {
    id: 'att-002', employeeId: 'emp-002', employeeName: 'Trần Thị Bình',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-05', checkIn: '08:12', checkOut: null,
    scheduledIn: '08:00', scheduledOut: '16:00',
    status: 'late', lateMinutes: 12, overtimeMinutes: 0,
  },
  {
    id: 'att-003', employeeId: 'emp-003', employeeName: 'Lê Hoàng Cường',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-05', checkIn: null, checkOut: null,
    scheduledIn: '08:00', scheduledOut: '16:00',
    status: 'on_leave', lateMinutes: 0, overtimeMinutes: 0,
    notes: 'Annual leave',
  },
  {
    id: 'att-004', employeeId: 'emp-004', employeeName: 'Phạm Minh Đức',
    outletId: 'outlet-002', outletName: 'Riverside Branch',
    date: '2026-04-05', checkIn: '13:55', checkOut: null,
    scheduledIn: '14:00', scheduledOut: '22:00',
    status: 'checked_in', lateMinutes: 0, overtimeMinutes: 0,
  },
  {
    id: 'att-005', employeeId: 'emp-005', employeeName: 'Vũ Thị Hoa',
    outletId: 'outlet-002', outletName: 'Riverside Branch',
    date: '2026-04-05', checkIn: null, checkOut: null,
    scheduledIn: '14:00', scheduledOut: '22:00',
    status: 'absent', lateMinutes: 0, overtimeMinutes: 0,
  },
  {
    id: 'att-006', employeeId: 'emp-006', employeeName: 'Đỗ Quang Huy',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-04', checkIn: '07:50', checkOut: '17:30',
    scheduledIn: '08:00', scheduledOut: '16:00',
    status: 'checked_out', lateMinutes: 0, overtimeMinutes: 90,
  },
  {
    id: 'att-007', employeeId: 'emp-007', employeeName: 'Hoàng Thị Lan',
    outletId: 'outlet-003', outletName: 'Mall Kiosk A',
    date: '2026-04-05', checkIn: '09:02', checkOut: null,
    scheduledIn: '09:00', scheduledOut: '17:00',
    status: 'checked_in', lateMinutes: 2, overtimeMinutes: 0,
  },
  {
    id: 'att-008', employeeId: 'emp-008', employeeName: 'Bùi Thanh Tùng',
    outletId: 'outlet-004', outletName: 'Uptown Express',
    date: '2026-04-04', checkIn: '06:00', checkOut: '15:45',
    scheduledIn: '06:00', scheduledOut: '14:00',
    status: 'checked_out', lateMinutes: 0, overtimeMinutes: 105,
  },
];

export const mockOvertime: OvertimeRecord[] = [
  {
    id: 'ot-001', employeeId: 'emp-006', employeeName: 'Đỗ Quang Huy',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-04', scheduledHours: 8, actualHours: 9.5,
    overtimeHours: 1.5, rate: 1.5, estimatedPay: 225000,
    status: 'pending', reason: 'Closing duties extended due to event',
  },
  {
    id: 'ot-002', employeeId: 'emp-008', employeeName: 'Bùi Thanh Tùng',
    outletId: 'outlet-004', outletName: 'Uptown Express',
    date: '2026-04-04', scheduledHours: 8, actualHours: 9.75,
    overtimeHours: 1.75, rate: 1.5, estimatedPay: 262500,
    status: 'pending', reason: 'Staff shortage cover',
  },
  {
    id: 'ot-003', employeeId: 'emp-001', employeeName: 'Nguyễn Văn An',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-03', scheduledHours: 8, actualHours: 10,
    overtimeHours: 2, rate: 1.5, estimatedPay: 300000,
    status: 'approved', approvedBy: 'Marcus Rivera',
    reason: 'Weekend rush support',
  },
  {
    id: 'ot-004', employeeId: 'emp-002', employeeName: 'Trần Thị Bình',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    date: '2026-04-02', scheduledHours: 8, actualHours: 9,
    overtimeHours: 1, rate: 2.0, estimatedPay: 200000,
    status: 'approved', approvedBy: 'Marcus Rivera',
    reason: 'Holiday shift (double rate)',
  },
  {
    id: 'ot-005', employeeId: 'emp-004', employeeName: 'Phạm Minh Đức',
    outletId: 'outlet-002', outletName: 'Riverside Branch',
    date: '2026-04-01', scheduledHours: 8, actualHours: 8.5,
    overtimeHours: 0.5, rate: 1.5, estimatedPay: 75000,
    status: 'rejected', reason: 'Unapproved extension',
  },
];

export const mockLeaveRequests: LeaveRequest[] = [
  {
    id: 'lv-001', employeeId: 'emp-003', employeeName: 'Lê Hoàng Cường',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    leaveType: 'annual', startDate: '2026-04-05', endDate: '2026-04-07',
    totalDays: 3, reason: 'Family vacation',
    status: 'approved', approvedBy: 'Marcus Rivera', createdAt: '2026-03-28',
  },
  {
    id: 'lv-002', employeeId: 'emp-005', employeeName: 'Vũ Thị Hoa',
    outletId: 'outlet-002', outletName: 'Riverside Branch',
    leaveType: 'sick', startDate: '2026-04-06', endDate: '2026-04-06',
    totalDays: 1, reason: 'Doctor appointment',
    status: 'pending', createdAt: '2026-04-04',
  },
  {
    id: 'lv-003', employeeId: 'emp-007', employeeName: 'Hoàng Thị Lan',
    outletId: 'outlet-003', outletName: 'Mall Kiosk A',
    leaveType: 'personal', startDate: '2026-04-10', endDate: '2026-04-11',
    totalDays: 2, reason: 'Moving to new apartment',
    status: 'pending', createdAt: '2026-04-03',
  },
  {
    id: 'lv-004', employeeId: 'emp-001', employeeName: 'Nguyễn Văn An',
    outletId: 'outlet-001', outletName: 'Downtown Flagship',
    leaveType: 'annual', startDate: '2026-04-15', endDate: '2026-04-18',
    totalDays: 4, reason: 'Travel abroad',
    status: 'pending', createdAt: '2026-04-01',
  },
  {
    id: 'lv-005', employeeId: 'emp-008', employeeName: 'Bùi Thanh Tùng',
    outletId: 'outlet-004', outletName: 'Uptown Express',
    leaveType: 'sick', startDate: '2026-03-20', endDate: '2026-03-21',
    totalDays: 2, reason: 'Flu',
    status: 'approved', approvedBy: 'Aisha Patel', createdAt: '2026-03-19',
  },
  {
    id: 'lv-006', employeeId: 'emp-004', employeeName: 'Phạm Minh Đức',
    outletId: 'outlet-002', outletName: 'Riverside Branch',
    leaveType: 'annual', startDate: '2026-03-10', endDate: '2026-03-10',
    totalDays: 1, reason: 'Personal matter',
    status: 'rejected', createdAt: '2026-03-05',
  },
];

export const mockLeaveQuotas: LeaveQuota[] = [
  {
    employeeId: 'emp-001', employeeName: 'Nguyễn Văn An', outletName: 'Downtown Flagship',
    annual: { total: 12, used: 3, remaining: 9 },
    sick: { total: 10, used: 1, remaining: 9 },
    personal: { total: 3, used: 0, remaining: 3 },
  },
  {
    employeeId: 'emp-002', employeeName: 'Trần Thị Bình', outletName: 'Downtown Flagship',
    annual: { total: 12, used: 5, remaining: 7 },
    sick: { total: 10, used: 2, remaining: 8 },
    personal: { total: 3, used: 1, remaining: 2 },
  },
  {
    employeeId: 'emp-003', employeeName: 'Lê Hoàng Cường', outletName: 'Downtown Flagship',
    annual: { total: 12, used: 6, remaining: 6 },
    sick: { total: 10, used: 0, remaining: 10 },
    personal: { total: 3, used: 2, remaining: 1 },
  },
  {
    employeeId: 'emp-004', employeeName: 'Phạm Minh Đức', outletName: 'Riverside Branch',
    annual: { total: 12, used: 2, remaining: 10 },
    sick: { total: 10, used: 3, remaining: 7 },
    personal: { total: 3, used: 0, remaining: 3 },
  },
  {
    employeeId: 'emp-005', employeeName: 'Vũ Thị Hoa', outletName: 'Riverside Branch',
    annual: { total: 12, used: 1, remaining: 11 },
    sick: { total: 10, used: 4, remaining: 6 },
    personal: { total: 3, used: 1, remaining: 2 },
  },
  {
    employeeId: 'emp-006', employeeName: 'Đỗ Quang Huy', outletName: 'Downtown Flagship',
    annual: { total: 15, used: 8, remaining: 7 },
    sick: { total: 10, used: 1, remaining: 9 },
    personal: { total: 3, used: 3, remaining: 0 },
  },
  {
    employeeId: 'emp-007', employeeName: 'Hoàng Thị Lan', outletName: 'Mall Kiosk A',
    annual: { total: 12, used: 0, remaining: 12 },
    sick: { total: 10, used: 0, remaining: 10 },
    personal: { total: 3, used: 0, remaining: 3 },
  },
  {
    employeeId: 'emp-008', employeeName: 'Bùi Thanh Tùng', outletName: 'Uptown Express',
    annual: { total: 12, used: 4, remaining: 8 },
    sick: { total: 10, used: 5, remaining: 5 },
    personal: { total: 3, used: 2, remaining: 1 },
  },
];
