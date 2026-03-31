import type { FernPrincipal } from '@core/auth/auth.types'
import { hasPermission } from './permission.checker'
import { permissionConstants } from './permission.constants'

/**
 * Export permission is intentionally separate from read permission.
 * A user may have report.read without report.export, and vice versa.
 * Always check export permission independently — do not infer from read.
 */

export function canExportGenericReports(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.report.export)
}

export function canExportPayrollReports(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.report.payrollExport)
}

export function canExportAnyReport(principal: FernPrincipal | null): boolean {
  return canExportGenericReports(principal) || canExportPayrollReports(principal)
}
