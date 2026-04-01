import type { FernPrincipal } from '@core/auth/auth.types'
import { hasPermission } from '@core/permissions/permission.checker'
import { canExportGenericReports, canExportPayrollReports } from '@core/permissions/exportPermission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import type { ExportDataset, ExportJob } from '../model/reportExport.types'

type ExportDatasetFamily = 'generic' | 'payroll'

const genericReportDatasets: ExportDataset[] = [
  'SALES_FACT',
  'PAYMENT_FACT',
  'INVENTORY_MOVEMENT_FACT',
  'PROCUREMENT_FACT',
  'ATTENDANCE_FACT',
  'EXPENSE_FACT',
  'REGION_DAILY_SUMMARY',
  'COMPANY_DAILY_SUMMARY',
]

const payrollReportDatasets: ExportDataset[] = [
  'PAYROLL_FACT',
  'PAYROLL_SUMMARY',
  'PAYROLL_RUN',
]

function resolveDatasetFamily(dataset: string): ExportDatasetFamily | null {
  if (genericReportDatasets.includes(dataset as ExportDataset)) {
    return 'generic'
  }

  if (payrollReportDatasets.includes(dataset as ExportDataset)) {
    return 'payroll'
  }

  return null
}

function canReadGenericReports(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.report.read)
}

function canReadPayrollReportsInternal(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.report.payrollRead)
}

function canReadExportRecord(principal: FernPrincipal | null, dataset: string) {
  const family = resolveDatasetFamily(dataset)

  if (family === 'generic') {
    return canReadGenericReports(principal)
  }

  if (family === 'payroll') {
    return canReadPayrollReportsInternal(principal)
  }

  return false
}

export function isExportPreviewAvailable(job: ExportJob): boolean {
  return ['RUNNING', 'COMPLETED'].includes(job.status.toUpperCase())
}

export function isExportDownloadAvailable(job: ExportJob): boolean {
  return job.status.toUpperCase() === 'COMPLETED'
}

export function canOpenExportJob(principal: FernPrincipal | null, job: ExportJob): boolean {
  return canReadExportRecord(principal, job.dataset)
}

export function canPreviewExport(principal: FernPrincipal | null, job: ExportJob): boolean {
  return canOpenExportJob(principal, job) && isExportPreviewAvailable(job)
}

export function canDownloadExport(principal: FernPrincipal | null, job: ExportJob): boolean {
  return canOpenExportJob(principal, job) && isExportDownloadAvailable(job)
}

export function getExportStatusDescription(job: ExportJob): string {
  switch (job.status.toUpperCase()) {
    case 'QUEUED':
      return 'Job has been queued and is waiting for a worker.'
    case 'RUNNING':
      return 'Job is being generated. Preview may already be available.'
    case 'COMPLETED':
      return 'Job finished successfully and can be previewed or downloaded.'
    case 'FAILED':
      return job.errorMessage || 'Job failed during export generation.'
    default:
      return 'Unknown export status.'
  }
}

export function canReadRevenueReport(principal: FernPrincipal | null) {
  return canReadGenericReports(principal)
}

export function canReadInventoryReport(principal: FernPrincipal | null) {
  return canReadGenericReports(principal)
}

export function canReadPayrollReport(principal: FernPrincipal | null) {
  return canReadPayrollReportsInternal(principal)
}

export function canCreateExport(principal: FernPrincipal | null, dataset?: ExportDataset) {
  if (!dataset) {
    // Delegate to centralized exportPermission.checker
    return canExportGenericReports(principal) || canExportPayrollReports(principal)
  }

  const family = resolveDatasetFamily(dataset)

  if (family === 'generic') {
    return canExportGenericReports(principal)
  }

  if (family === 'payroll') {
    return canExportPayrollReports(principal)
  }

  return false
}

export function getCreatableExportDatasets(principal: FernPrincipal | null): ExportDataset[] {
  return [...genericReportDatasets, ...payrollReportDatasets].filter((dataset) => canCreateExport(principal, dataset))
}

export function canReadPayrollReportDetail(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollDetailRead)
}

export function canInspectExportJobs(principal: FernPrincipal | null) {
  return canReadGenericReports(principal) || canReadPayrollReportsInternal(principal)
}

export function canViewExportJobs(principal: FernPrincipal | null) {
  return canInspectExportJobs(principal) || canCreateExport(principal)
}

export function canOpenReportDashboard(principal: FernPrincipal | null) {
  return canReadRevenueReport(principal)
    || canReadInventoryReport(principal)
    || canReadPayrollReport(principal)
    || canViewExportJobs(principal)
}
