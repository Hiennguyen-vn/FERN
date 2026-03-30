import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'
import type { ExportJob } from '../model/reportExport.types'

export function canPreviewExport(job: ExportJob): boolean {
  return ['RUNNING', 'COMPLETED'].includes(job.status.toUpperCase())
}

export function canDownloadExport(job: ExportJob): boolean {
  return job.status.toUpperCase() === 'COMPLETED'
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

const reportReadPermissions = [permissionConstants.report.read, permissionConstants.report.export]
const payrollReportPermissions = [permissionConstants.report.payrollRead, permissionConstants.report.payrollExport]

export function canReadReports(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, reportReadPermissions)
}

export function canExportReports(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.report.export)
}

export function canReadPayrollReports(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, payrollReportPermissions)
}

export function canExportPayrollReports(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.report.payrollExport)
}

export function canReadPayrollReportDetail(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollDetailRead)
}

export function canSeeReportsNavigation(principal: FernPrincipal | null) {
  return canReadReports(principal) || canReadPayrollReports(principal)
}
