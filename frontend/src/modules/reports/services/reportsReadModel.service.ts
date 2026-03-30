import type { ExportPreview } from '../model/reportExport.types'
import type {
  InventoryReportSummary,
  ReportInventoryTransaction,
  ReportStockBalance,
} from '../model/inventoryReport.types'
import type {
  ReportPayrollRunDetail,
  ReportPayrollSummary,
} from '../model/payrollReport.types'
import type { RevenueReportFilters, RevenueReportRunRequest, RevenueReportSummary } from '../model/revenueReport.types'

const dateFormatter = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' })

export function formatReportDateLabel(value: string | null | undefined) {
  if (!value) {
    return 'N/A'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return dateFormatter.format(date)
}

export function formatReportCurrency(value: number | string | null | undefined, currencyCode = 'VND') {
  if (value == null || value === '') {
    return '—'
  }

  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) {
    return String(value)
  }

  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: currencyCode,
    maximumFractionDigits: 0,
  }).format(numeric)
}

export function normalizeReportText(value: string | number | null | undefined) {
  return String(value ?? '')
    .trim()
    .toLowerCase()
}

export function matchesReportSearch(values: Array<string | number | null | undefined>, search: string) {
  const query = normalizeReportText(search)
  if (!query) {
    return true
  }

  return values.some((value) => normalizeReportText(value).includes(query))
}

export function parseNumericValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

function sumColumnByKeywords(rows: Array<Record<string, unknown>>, keywords: string[]) {
  const matchingKeys = new Set<string>()

  rows.forEach((row) => {
    Object.keys(row).forEach((key) => {
      const normalizedKey = key.toLowerCase()
      if (keywords.some((keyword) => normalizedKey.includes(keyword))) {
        matchingKeys.add(key)
      }
    })
  })

  if (matchingKeys.size === 0) {
    return null
  }

  const total = rows.reduce((sum, row) => {
    let rowSum = 0
    matchingKeys.forEach((key) => {
      const numeric = parseNumericValue(row[key])
      if (numeric !== null) {
        rowSum += numeric
      }
    })
    return sum + rowSum
  }, 0)

  return total
}

export function resolveRevenueDataset(regionId?: number): RevenueReportRunRequest['dataset'] {
  return regionId ? 'REGION_DAILY_SUMMARY' : 'COMPANY_DAILY_SUMMARY'
}

export function buildRevenueExportPayload(filters: RevenueReportFilters) {
  const dataset = resolveRevenueDataset(filters.regionId)
  return {
    dataset,
    format: 'CSV' as const,
    fromDate: filters.fromDate,
    limit: filters.limit,
    regionId: filters.regionId,
    toDate: filters.toDate,
  }
}

export function buildRevenueRunRequest(filters: RevenueReportFilters): RevenueReportRunRequest {
  return {
    ...filters,
    dataset: resolveRevenueDataset(filters.regionId),
  }
}

export function buildRevenueSummary(
  preview: ExportPreview | null | undefined,
): RevenueReportSummary {
  const rows = preview?.rows ?? []
  return {
    dimensionCount: rows.length > 0 ? Object.keys(rows[0]).filter((key) => parseNumericValue(rows[0][key]) === null).length : 0,
    jobStatus: preview?.status ?? null,
    rowCount: rows.length,
    totalDiscount: sumColumnByKeywords(rows, ['discount']),
    totalOrders: sumColumnByKeywords(rows, ['order_count', 'orders', 'qty_orders']),
    totalRevenue: sumColumnByKeywords(rows, ['revenue', 'amount', 'sales', 'net']),
  }
}

export function buildInventoryReportSummary(
  balances: ReportStockBalance[],
  transactions: ReportInventoryTransaction[],
): InventoryReportSummary {
  return {
    availableQuantity: balances.reduce((total, row) => total + Number(row.qtyAvailable ?? 0), 0),
    balanceRows: balances.length,
    ingredients: new Set(balances.map((row) => row.ingredientId)).size,
    transactionRows: transactions.length,
    txnQuantityDelta: transactions.reduce((total, row) => total + Number(row.qtyChange ?? 0), 0),
  }
}

export function buildPayrollDetailSummary(runDetail: ReportPayrollRunDetail | null | undefined) {
  const employees = runDetail?.employees ?? []
  const allocations = runDetail?.allocations ?? []

  return {
    allocationCount: allocations.length,
    employeeCount: employees.length,
    grossTotal: employees.reduce((total, employee) => total + Number(employee.grossPay ?? 0), 0),
    netTotal: employees.reduce((total, employee) => total + Number(employee.netPay ?? 0), 0),
    taxTotal: employees.reduce((total, employee) => total + Number(employee.taxAmount ?? 0), 0),
  }
}

export function buildReportDashboardSummary(jobCount: number, runningJobs: number, completedJobs: number) {
  return [
    {
      description: 'Recent jobs stored locally and refreshed from backend detail endpoints.',
      label: 'Recent exports',
      tone: 'info' as const,
      value: jobCount,
    },
    {
      description: 'Jobs still running or queued.',
      label: 'Pending exports',
      tone: 'warning' as const,
      value: runningJobs,
    },
    {
      description: 'Completed jobs ready for preview/download.',
      label: 'Completed exports',
      tone: 'success' as const,
      value: completedJobs,
    },
    {
      description: 'Revenue, inventory, payroll, and export center.',
      label: 'Published report surfaces',
      value: 4,
    },
  ]
}

export function buildPayrollSummaryCards(summary: ReportPayrollSummary | null | undefined) {
  return [
    {
      description: 'Gross pay for the selected region and date range.',
      label: 'Gross pay',
      tone: 'info' as const,
      value: formatReportCurrency(summary?.totalGrossPay),
    },
    {
      description: 'Net pay across payroll runs in the current filter.',
      label: 'Net pay',
      tone: 'success' as const,
      value: formatReportCurrency(summary?.totalNetPay),
    },
    {
      description: 'Tax recognized in the payroll reporting window.',
      label: 'Tax',
      tone: 'warning' as const,
      value: formatReportCurrency(summary?.totalTax),
    },
    {
      description: 'Payroll runs included in the summary query.',
      label: 'Run count',
      value: summary?.runCount ?? 0,
    },
  ]
}
