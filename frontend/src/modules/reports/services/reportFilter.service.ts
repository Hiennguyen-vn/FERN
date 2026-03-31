import type { CreateExportPayload, ExportDataset } from '../model/reportExport.types'

export function requiresDateRange(dataset: ExportDataset): boolean {
  return !['PAYROLL_RUN'].includes(dataset)
}

export function requiresRegion(dataset: ExportDataset): boolean {
  return !['COMPANY_DAILY_SUMMARY'].includes(dataset)
}

export function requiresPayrollRunId(dataset: ExportDataset): boolean {
  return dataset === 'PAYROLL_RUN'
}

export function normalizeExportPayload(payload: CreateExportPayload): CreateExportPayload {
  const filters = payload.filters ?? {}

  return {
    ...payload,
    fromDate: payload.fromDate ?? filters.fromDate,
    format: payload.format || 'CSV',
    limit: payload.limit ?? filters.limit,
    outletId: payload.outletId ?? filters.outletId,
    payrollRunId: payload.payrollRunId ?? filters.payrollRunId,
    regionId: payload.regionId ?? filters.regionId,
    toDate: payload.toDate ?? filters.toDate,
    filters: undefined,
  }
}
