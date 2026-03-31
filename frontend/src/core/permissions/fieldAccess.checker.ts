import type { FernPrincipal } from '@core/auth/auth.types'
import { hasPermission } from './permission.checker'
import { permissionConstants } from './permission.constants'

/**
 * Field-level access checkers.
 *
 * Backend applies field-level masking server-side — these functions mirror
 * the same permission rules so the frontend can show the correct UI mode
 * (masked vs. readonly-visible) without waiting for the API response.
 *
 * Rules:
 *  - Salary/tax fields on HR contracts: requires hr.contract.detail.read
 *  - Payroll employee-level breakdown: requires finance.payroll.detail.read
 *  - Audit event payload details: requires audit.detail.read
 *
 * Do NOT use these to gate data fetching — the backend controls what is
 * actually returned. Use these only to set field display mode.
 */

export function canViewContractSensitiveFields(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.hr.contractDetailRead)
}

export function canViewPayrollDetailFields(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.finance.payrollDetailRead)
}

export function canViewAuditEventDetail(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.audit.detailRead)
}
