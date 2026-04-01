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
  return {
    ...payload,
    format: payload.format ?? 'CSV',
  }
}
