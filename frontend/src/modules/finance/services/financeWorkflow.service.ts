import { formatMoney } from '@shared/formatters/formatMoney'
import type {
  FinancePaymentRequest,
  FinancePayrollRun,
  FinanceSupplier,
  RecentFinancePaymentRequestLookup,
} from '../model/finance.types'

const dateFormatter = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' })

export function formatFinanceDateLabel(value: string | null) {
  if (!value) {
    return 'N/A'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return dateFormatter.format(date)
}

export function formatFinanceCurrency(value: number | string | null | undefined, currencyCode = 'VND') {
  if (value == null) {
    return '—'
  }

  return formatMoney(value, currencyCode)
}

export function normalizeFinanceText(value: string | null | undefined) {
  return (value ?? '').trim().toLowerCase()
}

export function matchesFinanceSearch(values: Array<string | number | null | undefined>, search: string) {
  const query = normalizeFinanceText(search)
  if (!query) {
    return true
  }

  return values.some((value) => normalizeFinanceText(String(value ?? '')).includes(query))
}

export function buildSupplierLabel(
  supplierId: number,
  supplier?: Pick<FinanceSupplier, 'name' | 'supplierCode'> | null,
) {
  if (!supplier) {
    return `#${supplierId}`
  }

  return `${supplier.supplierCode} · ${supplier.name}`
}

export function buildPaymentRequestLabel(
  paymentRequest: Pick<FinancePaymentRequest, 'id' | 'invoiceNumber'>,
) {
  return `${paymentRequest.invoiceNumber} · #${paymentRequest.id}`
}

export function buildPayrollSummary(run: FinancePayrollRun) {
  const employees = run.employees ?? []
  const exceptions = employees.filter((employee) => employee.exceptionMessage)

  return {
    employeeCount: employees.length,
    exceptionCount: exceptions.length,
    totalNetPay: employees.reduce((total, employee) => total + Number(employee.netPay ?? 0), 0),
    totalAmount: run.totalAmount,
  }
}

export function filterRecentPaymentRequests(
  rows: RecentFinancePaymentRequestLookup[],
  search: string,
  statusFilter: string,
) {
  return rows.filter((row) => {
    const matchesStatus =
      statusFilter === 'ALL' || row.paymentRequest.status.toUpperCase() === statusFilter.toUpperCase()

    return (
      matchesStatus &&
      matchesFinanceSearch(
        [
          row.paymentRequest.id,
          row.paymentRequest.invoiceNumber,
          row.supplierCode,
          row.supplierName,
          row.paymentRequest.status,
        ],
        search,
      )
    )
  })
}
