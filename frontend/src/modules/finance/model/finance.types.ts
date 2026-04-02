export interface FinanceSupplier {
  id: number
  supplierCode: string
  name: string
  taxCode: string | null
  email: string | null
  phone: string | null
  address: string | null
  defaultRegionId: number | null
  status: string
  approvedAt: string | null
}

export interface FinancePaymentRequestLine {
  id: number
  lineNumber: number
  lineType: string | null
  goodsReceiptLineId: number | null
  description: string | null
  qtyInvoiced: number | null
  unitPrice: number | null
  taxPercent: number | null
  taxAmount: number | null
  lineTotal: number | null
  note: string | null
}

export interface FinancePaymentRequest {
  id: number
  supplierId: number
  regionId: number | null
  outletId: number | null
  currencyCode: string
  invoiceNumber: string
  invoiceDate: string
  dueDate: string | null
  subtotal: number | null
  taxAmount: number | null
  totalAmount: number | null
  status: string
  note: string | null
  approvedAt: string | null
  lines: FinancePaymentRequestLine[]
}

export interface FinancePayrollLine {
  id: number
  lineType: string
  description: string
  amount: number | null
}

export interface FinancePayrollAllocation {
  id: number
  outletId: number | null
  workHours: number | null
  allocatedAmount: number | null
}

export interface FinancePayrollEmployeeResult {
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
  lines: FinancePayrollLine[]
  allocations: FinancePayrollAllocation[]
}

export interface FinancePayrollRun {
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
  employees: FinancePayrollEmployeeResult[]
}

export interface ReviewPayrollRunPayload {
  note?: string
}

export interface MarkPayrollPaidPayload {
  note?: string
  paymentReference: string
}

export interface FinancePayrollFilters {
  regionId?: number
}

export interface RecentFinancePaymentRequestLookup {
  paymentRequest: FinancePaymentRequest
  supplierCode?: string | null
  supplierName?: string | null
}

// ─── Finance Config ───────────────────────────────────────────────────────────

export interface NumberingRule {
  id: number
  documentType: string
  prefix: string | null
  regionId: number | null
  outletId: number | null
  nextNumber: number
  resetPeriod: string | null
  formatPattern: string | null
  active: boolean
}

export interface SystemPolicy {
  policyKey: string
  // policyValue is a free-form JSON node on the backend; we treat it as unknown here
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  policyValue: any
  description: string | null
}

export interface PutNumberingRulePayload {
  prefix?: string | null
  regionId?: number | null
  outletId?: number | null
  nextNumber?: number | null
  resetPeriod?: string | null
  formatPattern?: string | null
  active?: boolean | null
}

export interface PutSystemPolicyPayload {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  policyValue: any
  description?: string | null
}
