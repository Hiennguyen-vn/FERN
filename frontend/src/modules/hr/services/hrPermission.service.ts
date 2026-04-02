import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const hrNavigationPermissions = [
  permissionConstants.hr.employeeRead,
  permissionConstants.hr.employeeWrite,
  permissionConstants.hr.contractRead,
  permissionConstants.hr.contractWrite,
  permissionConstants.hr.shiftRead,
  permissionConstants.hr.shiftWrite,
  permissionConstants.hr.payrollPrepare,
  permissionConstants.hr.attendanceReview,
  permissionConstants.finance.payrollRead,
  permissionConstants.finance.payrollPrepare,
]

export function canReadEmployees(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.employeeRead)
}

export function canWriteEmployees(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.employeeWrite)
}

export function canReadContracts(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.contractRead)
}

export function canWriteContracts(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.contractWrite)
}

export function canReadContractDetail(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.contractDetailRead)
}

export function canReadAssignments(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.shiftRead)
}

export function canWriteAssignments(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.shiftWrite)
}

export function canReadAttendanceSummary(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.hr.attendanceReview)
}

export function canReadPayroll(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollRead)
}

export function canReadPayrollDetail(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.finance.payrollDetailRead)
}

export function canPreparePayroll(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, [permissionConstants.hr.payrollPrepare, permissionConstants.finance.payrollPrepare])
}

export function canSeeHrNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, hrNavigationPermissions)
}
