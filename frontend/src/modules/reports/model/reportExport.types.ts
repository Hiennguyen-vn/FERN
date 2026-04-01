export type ExportDataset =
  | 'SALES_FACT'
  | 'PAYMENT_FACT'
  | 'INVENTORY_MOVEMENT_FACT'
  | 'PROCUREMENT_FACT'
  | 'ATTENDANCE_FACT'
  | 'PAYROLL_FACT'
  | 'EXPENSE_FACT'
  | 'REGION_DAILY_SUMMARY'
  | 'COMPANY_DAILY_SUMMARY'
  | 'PAYROLL_SUMMARY'
  | 'PAYROLL_RUN'

export type ExportFormat = 'CSV'
export type ExportJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface CreateExportPayload {
  dataset: ExportDataset
  format?: ExportFormat
  fromDate?: string
  limit?: number
  outletId?: number
  payrollRunId?: number
  regionId?: number
  toDate?: string
}

export interface ExportJob {
  exportJobId: number
  status: string
  dataset: string
  format: string
  requestedAt: string
  startedAt: string | null
  completedAt: string | null
  failedAt: string | null
  rowCount: number | null
  downloadUrl: string | null
  expiresAt: string | null
  errorMessage: string | null
  filePath: string | null
  preview: Array<Record<string, unknown>>
}

export interface ExportPreview {
  exportJobId: number
  status: string
  dataset: string
  rowCount: number | null
  rows: Array<Record<string, unknown>>
}

export interface ExportJobListFilters {
  dataset?: ExportDataset
  outletId?: number
  page?: number
  regionId?: number
  size?: number
  status?: ExportJobStatus
}
