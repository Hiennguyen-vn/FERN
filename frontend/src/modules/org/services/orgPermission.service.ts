import type { FernPrincipal } from '@core/auth/auth.types'
import { hasAnyPermissions, hasPermission } from '@core/permissions/permission.checker'
import { permissionConstants } from '@core/permissions/permission.constants'

const orgNavigationPermissions = [
  permissionConstants.org.regionRead,
  permissionConstants.org.outletRead,
  permissionConstants.org.regionWrite,
  permissionConstants.org.outletWrite,
]

export function canReadRegions(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.org.regionRead)
}

export function canWriteRegions(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.org.regionWrite)
}

export function canReadOutlets(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.org.outletRead)
}

export function canWriteOutlets(principal: FernPrincipal | null) {
  return hasPermission(principal, permissionConstants.org.outletWrite)
}

export function canSeeOrgNavigation(principal: FernPrincipal | null) {
  return hasAnyPermissions(principal, orgNavigationPermissions)
}
