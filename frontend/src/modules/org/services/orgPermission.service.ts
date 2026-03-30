import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const orgNavigationPermissions = [
  permissionConstants.org.regionRead,
  permissionConstants.org.outletRead,
]

export function canReadRegions(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.org.regionRead)
}

export function canReadOutlets(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.org.outletRead)
}

export function canSeeOrgNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, orgNavigationPermissions)
}
