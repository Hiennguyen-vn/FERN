import type {
  HrAttendanceApproval,
  HrAttendanceEventListItem,
  HrContract,
  HrEmployee,
  PayrollRun,
} from '../model/hr.types'

const dateFormatter = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' })

export function normalizeText(value: string | null | undefined) {
  return (value ?? '').trim().toLowerCase()
}

export function matchesSearch(values: Array<string | number | null | undefined>, search: string) {
  const query = normalizeText(search)
  if (!query) {
    return true
  }

  return values.some((value) => normalizeText(String(value ?? '')).includes(query))
}

export function formatDateLabel(value: string | null) {
  if (!value) {
    return 'Open-ended'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return dateFormatter.format(date)
}

export function formatDateRange(start: string | null, end: string | null) {
  return `${formatDateLabel(start)} → ${formatDateLabel(end)}`
}

export function formatCurrencyAmount(amount: number | null, currencyCode = 'VND') {
  if (amount == null) {
    return '—'
  }

  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currencyCode,
      maximumFractionDigits: 0,
    }).format(amount)
  } catch {
    return `${amount.toLocaleString('vi-VN')} ${currencyCode}`
  }
}

export function formatDecimal(value: number | null, suffix = '') {
  if (value == null) {
    return '—'
  }

  return `${value.toLocaleString('vi-VN')}${suffix}`
}

export function buildEmployeeLabel(employee: HrEmployee | null | undefined) {
  if (!employee) {
    return 'Unknown employee'
  }

  return `${employee.employeeCode} · ${employee.fullName}`
}

export function buildContractLabel(contract: HrContract) {
  return `#${contract.id} · ${contract.employmentType}`
}

export function buildAttendanceSummary(events: HrAttendanceEventListItem[], approvals: HrAttendanceApproval[]) {
  const uniqueEmployees = new Set(events.map((event) => event.employeeId)).size
  const pendingApprovals = approvals.filter((approval) => approval.status.toUpperCase() === 'PENDING').length
  const exceptions = approvals.filter((approval) => isAttendanceException(approval)).length

  return {
    exceptionCount: exceptions,
    pendingApprovals,
    totalEvents: events.length,
    uniqueEmployees,
  }
}

export function isAttendanceException(approval: HrAttendanceApproval) {
  const status = approval.status.toUpperCase()
  const attendanceStatus = approval.attendanceStatus.toUpperCase()
  const overtimeHours = Number(approval.overtimeHours ?? 0)

  return status !== 'APPROVED' || attendanceStatus !== 'PRESENT' || overtimeHours > 0
}

export function buildPayrollSummary(run: PayrollRun) {
  const employees = run.employees ?? []
  const exceptions = employees.filter((employee) => employee.exceptionMessage)

  return {
    employeeCount: employees.length,
    exceptionCount: exceptions.length,
    totalAmount: run.totalAmount,
    totalNetPay: employees.reduce((total, employee) => total + (employee.netPay ?? 0), 0),
  }
}
