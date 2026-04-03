import type { ExportDataset, ExportJob, ExportPreview } from './reportExport.types'

export interface RevenueReportFilters {
  fromDate: string
  limit: number
  regionId?: number
  toDate: string
}

export interface RevenueReportRunRequest extends RevenueReportFilters {
  dataset: Extract<ExportDataset, 'REGION_DAILY_SUMMARY' | 'COMPANY_DAILY_SUMMARY'>
}

export interface RevenueReportSummary {
  dimensionCount: number
  jobStatus: string | null
  rowCount: number
  totalDiscount: number | null
  totalOrders: number | null
  totalRevenue: number | null
}

export interface OutletRevenueTodayStat {
  outletId: number
  sessionId: number | null
  sessionStatus: string
  currencyCode: string
  totalOrders: number
  completed: number
  open: number
  cancelled: number
  totalRevenue: number
  cashCollected: number
  nonCashCollected: number
}

export interface RevenueReportState {
  filters: RevenueReportRunRequest | null
  job: ExportJob | null
  preview: ExportPreview | null
  summary: RevenueReportSummary
}
