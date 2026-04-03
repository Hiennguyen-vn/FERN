import type { PageResponse } from '@core/types/api'

export interface HrEmployee {
  id: number
  employeeCode: string
  fullName: string
  dob: string | null
  gender: string | null
  email: string | null
  phone: string | null
  status: string
  hiredAt: string | null
  userAccountId: number | null
}

export interface CreateEmployeePayload {
  employeeCode?: string | null
  fullName: string
  dob?: string | null
  gender?: string | null
  email?: string | null
  phone?: string | null
  status?: string | null
  hiredAt?: string | null
  userAccountId?: number | null
}

export interface HrContract {
  id: number
  employeeId: number
  employmentType: string
  salaryType: string
  baseSalary: number | null
  regionId: number | null
  taxCode: string | null
  contractStatus: string
  startDate: string
  endDate: string | null
}

export interface CreateContractPayload {
  employeeId: number
  employmentType: string
  salaryType: string
  baseSalary: number
  regionId?: number | null
  taxCode?: string | null
  contractStatus?: string | null
  startDate: string
  endDate?: string | null
}

export interface HrAssignment {
  id: number
  employeeId: number
  regionId: number
  outletId: number
  positionTitle: string
  startDate: string
  endDate: string | null
  primaryAssignment: boolean
  status: string
}

export interface CreateAssignmentPayload {
  employeeId: number
  regionId: number
  outletId: number
  positionTitle: string
  startDate: string
  endDate?: string | null
  primaryAssignment?: boolean | null
  status?: string | null
}

export interface HrAttendanceEventDetail {
  id: number
  employeeId: number
  shiftAssignmentId: number
  eventType: string
  eventTime: string
  sourceSystem: string | null
}

export interface HrAttendanceEventListItem extends HrAttendanceEventDetail {
  regionId: number
  outletId: number
  shiftDate: string
}

/** @deprecated Use HrAttendanceEventListItem or HrAttendanceEventDetail */
export type HrAttendanceEvent = HrAttendanceEventListItem

export interface HrAttendanceApproval {
  id: number | null
  shiftAssignmentId: number
  status: string
  comments: string | null
  approvedAt: string | null
  approvedByUserId: number | null
  attendanceStatus: string
  workHours: number | null
  overtimeHours: number | null
  businessDate: string | null
}

export interface HrAttendanceFilters {
  employeeId?: number
  fromDate?: string
  outletId?: number
  page: number
  regionId?: number
  shiftAssignmentId?: number
  size: number
  sort?: string
  toDate?: string
}

export type HrAttendanceEventPage = PageResponse<HrAttendanceEventListItem>

export interface PayrollPeriod {
  id: number
  regionId: number
  referenceCode: string
  name: string
  startDate: string
  endDate: string
  payDate: string | null
  status: string
  note: string | null
}

export interface PayrollLine {
  id: number
  lineType: string
  description: string
  amount: number | null
}

export interface PayrollAllocation {
  id: number
  outletId: number | null
  workHours: number | null
  allocatedAmount: number | null
}

export interface PayrollEmployeeResult {
  id: number
  employeeId: number | null
  contractId: number | null
  outletId: number | null
  grossPay: number | null
  deductionAmount: number | null
  taxAmount: number | null
  netPay: number | null
  workDays: number | null
  workHours: number | null
  overtimeHours: number | null
  paymentStatus: string
  exceptionMessage: string | null
  lines: PayrollLine[]
  allocations: PayrollAllocation[]
}

export interface PayrollRun {
  id: number
  payrollPeriodId: number
  runCode: string
  runDate: string
  status: string
  totalAmount: number | null
  paymentRef: string | null
  note: string | null
  submittedAt: string | null
  approvedAt: string | null
  paidAt: string | null
  employees: PayrollEmployeeResult[]
}

export interface CreatePayrollPeriodPayload {
  endDate: string
  name: string
  note?: string
  payDate?: string
  regionId: number
  startDate: string
}

export interface CreatePayrollRunPayload {
  note?: string
  payrollPeriodId: number
  runDate?: string
}

export interface RecentHrContractLookup {
  contract: HrContract
  employeeCode?: string | null
  employeeId: number
  employeeName?: string | null
}

export interface ShiftSchedule {
  id: number
  regionId: number
  outletId: number
  shiftDate: string
  shiftName: string
  startTime: string
  endTime: string
  status: string
}

export interface ShiftAssignment {
  id: number
  shiftScheduleId: number
  employeeId: number
  assignedRole: string | null
  attendanceStatus: string
  approvalStatus: string
  note: string | null
}

export interface CreateShiftSchedulePayload {
  regionId: number
  outletId: number
  shiftDate: string
  shiftName: string
  startTime: string
  endTime: string
  status?: string
}

export interface CreateShiftAssignmentPayload {
  shiftScheduleId: number
  employeeId: number
  assignedRole?: string
  note?: string
}

export type AttendanceEventType = 'CLOCK_IN' | 'CLOCK_OUT' | 'BREAK_START' | 'BREAK_END'

export interface RecordAttendanceEventPayload {
  employeeId: number
  regionId: number
  outletId: number
  shiftAssignmentId: number
  eventType: AttendanceEventType
  eventTime: string
  sourceSystem?: string | null
}

export interface ReviewAttendancePayload {
  comments?: string | null
}

// ─── Missing backend response types ──────────────────────────────────────────

export interface EffectiveContractResponse {
  contractId: number
  employeeId: number
  regionId: number | null
  employmentType: string
  salaryType: string
  baseSalary: number
  taxCode: string | null
  startDate: string
  endDate: string | null
}

export interface ApprovedAttendanceResponse {
  approvalId: number
  shiftAssignmentId: number
  employeeId: number
  regionId: number
  outletId: number
  contractId: number
  businessDate: string
  attendanceStatus: string
  workHours: number
  overtimeHours: number
}

export interface AttendanceApprovalListResponse {
  items: ApprovedAttendanceResponse[]
}
