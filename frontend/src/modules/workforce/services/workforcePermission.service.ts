import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

export function canRecordAttendance(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.hr.attendanceWrite)
}

export function canApproveAttendance(principal: FernPrincipal | null): boolean {
  return hasPermission(principal, permissionConstants.hr.attendanceReview)
}

/**
 * Returns true if the principal has any workforce attendance capability.
 * Used to determine whether the module is accessible at all (mirrors the
 * router-level RequireAnyPermission guard).
 */
export function canAccessWorkforceModule(principal: FernPrincipal | null): boolean {
  return hasAnyPermissions(principal, [
    permissionConstants.hr.attendanceWrite,
    permissionConstants.hr.attendanceReview,
  ])
}
