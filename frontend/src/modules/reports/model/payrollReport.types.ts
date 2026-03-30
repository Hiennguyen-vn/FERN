export interface ReportPayrollSummaryFilters {
  fromDate: string
  regionId: number
  toDate: string
}

export interface ReportPayrollSummary {
  fromDate: string
  regionId: number
  runCount: number
  toDate: string
  totalExpense: number | null
  totalGrossPay: number | null
  totalNetPay: number | null
  totalTax: number | null
}

export interface ReportPayrollRunEmployee {
  businessDate: string
  employeeId: number
  grossPay: number | null
  netPay: number | null
  outletId: number | null
  taxAmount: number | null
}

export interface ReportPayrollRunAllocation {
  outletId: number | null
  totalAmount: number | null
}

export interface ReportPayrollRunDetail {
  allocations: ReportPayrollRunAllocation[]
  employees: ReportPayrollRunEmployee[]
  payrollRunId: number
}

export interface ReportPayrollRunListItem {
  approvedAt: string | null
  id: number
  note: string | null
  paidAt: string | null
  paymentRef: string | null
  payrollPeriodId: number
  runCode: string
  runDate: string
  status: string
  submittedAt: string | null
  totalAmount: number | null
}
