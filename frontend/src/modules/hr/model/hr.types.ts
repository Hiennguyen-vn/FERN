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

export interface HrAttendanceEvent {
  id: number
  employeeId: number
  regionId: number
  outletId: number
  shiftAssignmentId: number
  shiftDate: string
  eventType: string
  eventTime: string
  sourceSystem: string | null
}

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

export type HrAttendanceEventPage = PageResponse<HrAttendanceEvent>

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
